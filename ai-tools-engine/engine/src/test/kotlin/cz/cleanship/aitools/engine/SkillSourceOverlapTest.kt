package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.io.PathOverlap
import cz.cleanship.aitools.engine.io.SymbolicLink
import cz.cleanship.aitools.engine.models.ToolType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.nio.file.Path

class SkillSourceOverlapTest {

    @ParameterizedTest
    @CsvSource(
        // - a replaced directory holding a link into the source folder is only unlinked, so the message must not claim the source would be damaged
        "true, true, false",
        // - a replaced directory that is or holds the source folder itself would delete it
        "true, false, true",
        // - a skill written through a link into the source folder would overwrite it
        "false, true, true",
        "false, false, true",
    )
    fun `should claim harm to the source only when the deploy would really overwrite or delete it`(
        replace: Boolean,
        throughLink: Boolean,
        claimsHarm: Boolean,
    ) {
        // given
        val link = SymbolicLink(File("/project/.claude/skills"), Path.of("/checkout/skills")).takeIf { throughLink }
        val action = if (replace) DeployAction.Replace else DeployAction.WriteSkill("jira-ticket")
        val overlap = overlap(action, link)

        // when
        val message = overlap.message

        // then
        assertThat(message.contains("Deploying would overwrite or delete the files of the source.")).isEqualTo(claimsHarm)
        assertThat(message.contains("the link '/project/.claude/skills' below the directory to be replaced leads to '/checkout/skills'")).isEqualTo(replace && throughLink)
    }

    @ParameterizedTest
    @CsvSource("true", "false")
    fun `should advise removing the link or turning off replace when a link below a replaced directory leads to the source`(
        throughLink: Boolean,
    ) {
        // given
        val link = SymbolicLink(File("/project/.claude/skills"), Path.of("/checkout/skills")).takeIf { throughLink }
        val overlap = overlap(DeployAction.Replace, link)

        // when
        val message = overlap.message

        // then
        assertThat(message.endsWith("Remove the link '/project/.claude/skills', or turn off 'replace' for that deployment.")).isEqualTo(throughLink)
    }

    /**
     * Builds the overlap of the replaced or written directory '/project/.claude' of the project 'demo' with the source folder '/checkout/skills/jira-ticket', which it contains.
     */
    private fun overlap(action: DeployAction, link: SymbolicLink?) = SkillSourceOverlap(
        skillId = "jira-ticket",
        manifestFile = null,
        sourceDir = File("/checkout/skills/jira-ticket"),
        action = action,
        path = File("/project/.claude"),
        link = link,
        overlap = PathOverlap.CONTAINS,
        toolType = ToolType.CLAUDE,
        deployedBy = "project 'demo'",
    )
}
