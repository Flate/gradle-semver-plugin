package io.github.nefilim.gradle.semver.config

import arrow.core.None
import arrow.core.Option
import net.swiftzer.semver.SemVer
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.gradle.api.Project

data class PluginConfig(
    val verbose: Boolean = true,
    val tagPrefix: String,
    val initialVersion: SemVer,
    val overrideVersion: Option<SemVer> = None,
    val featureBranchRegexes: List<Regex> = emptyList(),

    val currentBranchScope: Option<Scope> = None,
    val currentBranchStage: Option<Stage> = None,
) {
    companion object {
        internal val DefaultVersion = SemVer(0, 1, 0, null, null)
        internal const val DefaultTagPrefix = "v"
    }

    fun flatMode(): PluginConfig {
        return this.copy(featureBranchRegexes = this.featureBranchRegexes + """.*""".toRegex())
    }
}

data class SemVerPluginContext(
    val git: Git,
    val config: PluginConfig,
    val project: Project,
) {
    val repository: Repository = git.repository
}

enum class Stage(private val value: String) {
    Alpha("alpha"),
    Beta("beta"),
    RC("rc"),
    Final("final"),
    Snapshot("snapshot"),
    Branch("branch");

    operator fun invoke(): String = value

    companion object {
        private val map = values().associateBy { it.value }

        fun fromValue(value: String?, default: Stage): Stage {
            return if (value != null )
                map[value.lowercase()] ?: throw IllegalArgumentException("invalid stage: [${value.lowercase()}], valid values: ${values().joinToString { it.value }}")
            else
                default
        }

        fun fromValue(value: String): Stage {
            return map[value.lowercase()] ?: throw IllegalArgumentException("invalid stage: [${value.lowercase()}], valid values: ${values().joinToString { it.value }}")
        }
    }
}

enum class Scope(private val value: String) {
    Major("major"),
    Minor("minor"),
    Patch("patch");

    operator fun invoke(): String = value

    companion object {
        private val map = values().associateBy { it.value }

        fun fromValue(value: String?, default: Scope): Scope {
            return if (value != null )
                map[value.lowercase()] ?: throw IllegalArgumentException("invalid scope: [${value.lowercase()}], valid values: ${values().joinToString { it.value }}")
            else
                default
        }

        fun fromValue(value: String): Scope {
            return map[value.lowercase()] ?: throw IllegalArgumentException("invalid scope: [${value.lowercase()}], valid values: ${values().joinToString { it.value }}")
        }
    }
}

fun possiblyPrefixedVersion(version: String, prefix: String): SemVer {
    return SemVer.parse(version.trimMargin(prefix)) // fail fast, don't let an invalid version propagate to runtime
}