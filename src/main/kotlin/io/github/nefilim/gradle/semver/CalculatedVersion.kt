package io.github.nefilim.gradle.semver

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.github.nefilim.gradle.semver.config.Scope
import io.github.nefilim.gradle.semver.config.SemVerPluginContext
import io.github.nefilim.gradle.semver.config.Stage
import io.github.nefilim.gradle.semver.domain.GitRef
import io.github.nefilim.gradle.semver.domain.SemVerError
import net.swiftzer.semver.SemVer

@Suppress("ComplexMethod")
internal fun SemVerPluginContext.calculatedVersionFlow(
    main: GitRef.MainBranch,
    develop: GitRef.DevelopBranch,
    currentBranch: GitRef.Branch,
): Either<SemVerError, SemVer> {
    val tags = git.tagMap(config.tagPrefix)
    verbose("calculating version for current branch $currentBranch, main: $main, develop: $develop")
    return when (currentBranch) {
        is GitRef.MainBranch -> {
            main.version.fold({
                warn("unable to determine last version on main branch, using initialVersion [${config.initialVersion}]")
                config.initialVersion.right()
            },{
                applyScopeToVersion(currentBranch, it, main.scope, main.stage)
            })
        }
        is GitRef.DevelopBranch -> {
            // recalculate version automatically based on releases on main
            either.eager {
                val mainVersion = git.calculateBaseBranchVersion(main, develop, tags).bind()
                val branchPoint = git.headRevInBranch(main).bind()
                mainVersion.getOrElse {
                    warn("unable to determine last version from main branch, using initialVersion [${config.initialVersion}] as base")
                    config.initialVersion
                }.let {
                    applyScopeToVersion(currentBranch, it, currentBranch.scope, currentBranch.stage).map {
                        qualifyStage(currentBranch, it) { commitsSinceBranchPoint(branchPoint, currentBranch, tags).getOrElse { 0 } }
                    }
                }.bind()
            }
        }
        is GitRef.FeatureBranch -> {
            // must have been branched from develop
            either.eager {
                val devVersion = git.calculateBaseBranchVersion(develop, currentBranch, tags).bind()
                val branchPoint = git.headRevInBranch(develop).bind()
                devVersion.fold({
                    SemVerError.MissingVersion("unable to find version tag on develop branch, feature branches must be branched from develop").left()
                }, {
                    applyScopeToVersion(currentBranch, it, currentBranch.scope, currentBranch.stage).map {
                        qualifyStage(currentBranch, it) { commitsSinceBranchPoint(branchPoint, currentBranch, tags).getOrElse { 0 } }
                    }
                }).bind()
            }
        }
        is GitRef.HotfixBranch -> {
            // must have been branched from main
            either.eager {
                val devVersion = git.calculateBaseBranchVersion(main, currentBranch, tags).bind()
                val branchPoint = git.headRevInBranch(main).bind()
                devVersion.fold({
                    SemVerError.MissingVersion("unable to find version tag on main branch, hotfix branches must be branched from main").left()
                }, {
                    applyScopeToVersion(currentBranch, it, currentBranch.scope, currentBranch.stage).map {
                        qualifyStage(currentBranch, it) { commitsSinceBranchPoint(branchPoint, currentBranch, tags).getOrElse { 0 } }
                    }
                }).bind()
            }
        }
    }
}

internal fun SemVerPluginContext.calculatedVersionFlat(
    main: GitRef.MainBranch,
    currentBranch: GitRef.Branch,
): Either<SemVerError, SemVer> {
    val tags = git.tagMap(config.tagPrefix)
    verbose("calculating flat version for current branch $currentBranch, main: $main")
    return when (currentBranch) {
        is GitRef.MainBranch -> {
            main.version.fold({
                warn("unable to determine last version on main branch, using initialVersion [${config.initialVersion}]")
                config.initialVersion.right()
            },{
                applyScopeToVersion(currentBranch, it, main.scope, main.stage)
            })
        }
        else -> {
            // recalculate version automatically based on releases on main
            either.eager {
                val mainVersion = git.calculateBaseBranchVersion(main, currentBranch, tags).bind()
                val branchPoint = git.headRevInBranch(main).bind()
                mainVersion.getOrElse {
                    warn("unable to determine last version from main branch, using initialVersion [${config.initialVersion}] as base")
                    config.initialVersion
                }.let {
                    applyScopeToVersion(currentBranch, it, currentBranch.scope, currentBranch.stage).map {
                        qualifyStage(currentBranch, it) { commitsSinceBranchPoint(branchPoint, currentBranch, tags).getOrElse { 0 } }
                    }
                }.bind()
            }
        }
    }
}

internal fun applyScopeToVersion(currentBranch: GitRef.Branch, version: SemVer, scope: Scope, stage: Stage): Either<SemVerError, SemVer> {
    return when (scope) {
        Scope.Major -> version.nextMajor()
        Scope.Minor -> version.nextMinor()
        Scope.Patch -> version.nextPatch()
    }.let {
        if (currentBranch is GitRef.MainBranch)
            it.right()
        else
            it.copy(preRelease = stage.toStageName(currentBranch)).right()
    }
}

internal fun qualifyStage(
    currentBranch: GitRef.Branch,
    version: SemVer,
    qualifier: () -> Int
): SemVer {
    return when(currentBranch.stage) {
        Stage.Snapshot, Stage.Final -> version // Snapshot & Final does not need to be qualified
        else -> version.copy(preRelease = "${version.preRelease}.${qualifier()}")
    }
}

internal fun SemVer.applyStageNumber(stageNumber: Int): SemVer {
    return this.copy(preRelease = if (this.preRelease.isNullOrBlank()) "1" else "${this.preRelease}.$stageNumber")
}

// TODO create an ADT for Stage so we can derive string name here
internal fun Stage?.toStageName(currentBranch: GitRef.Branch): String? {
    return when (this) {
        null -> null
        Stage.Final -> null
        Stage.Snapshot -> "SNAPSHOT"
        Stage.Branch -> currentBranch.name.substringAfterLast('/')
        else -> this.name.lowercase()
    }
}