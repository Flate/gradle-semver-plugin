import io.github.nefilim.gradle.semver.semverTag
import io.kotest.assertions.arrow.core.shouldBeNone
import io.kotest.core.spec.style.WordSpec

// Kotest is not functional until Gradle gets its act together and move to 1.6: https://github.com/kotest/kotest/issues/2785
//class SemVerSpec: WordSpec() {
//    init {
//        "SemVer" should {
//            "match valid existing semver tags on refs" {
//                "refs/tags/v123".semverTag("v").shouldBeNone()
//                "refs/tags/v1.2.3".semverTag("v").shouldBeNone()
//            }
//        }
//    }
//}