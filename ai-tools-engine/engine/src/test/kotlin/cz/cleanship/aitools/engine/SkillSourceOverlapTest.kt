package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.io.PathOverlap
import cz.cleanship.aitools.engine.io.SymbolicLink
import cz.cleanship.aitools.engine.models.ToolType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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

    @Test
    fun `should say the deploy never opens what it leads to when the replaced directory itself is a link leading to the source`() {
        // given
        // - '/project/.claude' is itself a link to '/checkout', which holds the source folder; a replacing deploy never opens what it leads to, whether it unlinks it in a project or refuses it in the home
        val overlap = overlap(DeployAction.Replace, SymbolicLink(File("/project/.claude"), Path.of("/checkout")))

        // when
        val message = overlap.message

        // then
        assertThat(message).isEqualTo(
            "Skill 'jira-ticket' is read from the source folder '/checkout/skills/jira-ticket', but claude would delete '/project/.claude' for project 'demo' to replace it, " +
                "and '/project/.claude' is itself a link to '/checkout', which contains that folder. " +
                "A replacing deploy never opens what it leads to, but it refuses while a replaced directory links into a source folder. " +
                "Remove the link '/project/.claude', or turn off 'replace' for that deployment.",
        )
    }

    @Test
    fun `should say where the skill lands once the replaced link is removed and advise moving the project when that lies inside the source folder`() {
        // given
        // - the project deploys inside the source folder '/src/jira-ticket', and its replaced '.claude' is a link leading elsewhere, so the skill lands in the source once that link is removed
        val overlap = SkillSourceOverlap(
            skillId = "jira-ticket",
            manifestFile = null,
            sourceDir = File("/src/jira-ticket"),
            action = DeployAction.WriteSkill("jira-ticket"),
            path = File("/src/jira-ticket/proj/.claude/skills/jira-ticket"),
            link = null,
            overlap = PathOverlap.INSIDE,
            toolType = ToolType.CLAUDE,
            deployedBy = "project 'demo'",
            unlinked = File("/src/jira-ticket/proj/.claude"),
        )

        // when
        val message = overlap.message

        // then
        assertThat(message).isEqualTo(
            "Skill 'jira-ticket' is read from the source folder '/src/jira-ticket', but claude would write the skill 'jira-ticket' for project 'demo' to '/src/jira-ticket/proj/.claude/skills/jira-ticket', " +
                "which lies inside that folder once the link '/src/jira-ticket/proj/.claude' is removed. " +
                "Deploying would overwrite or delete the files of the source. " +
                "Move the directory that project 'demo' deploys to out of the source folder, or turn off 'replace' for that deployment.",
        )
    }

    @Test
    fun `should say where the replaced link sits and advise moving the project when that lies inside the source folder`() {
        // given
        // - the replaced '.claude' is itself a link leading elsewhere, but the entry it is sits inside the source folder '/src/jira-ticket'
        val overlap = SkillSourceOverlap(
            skillId = "jira-ticket",
            manifestFile = null,
            sourceDir = File("/src/jira-ticket"),
            action = DeployAction.Replace,
            path = File("/src/jira-ticket/proj/.claude"),
            link = null,
            overlap = PathOverlap.INSIDE,
            toolType = ToolType.CLAUDE,
            deployedBy = "project 'demo'",
            unlinked = File("/src/jira-ticket/proj/.claude"),
        )

        // when
        val message = overlap.message

        // then
        assertThat(message).isEqualTo(
            "Skill 'jira-ticket' is read from the source folder '/src/jira-ticket', but claude would delete '/src/jira-ticket/proj/.claude' for project 'demo' to replace it, " +
                "which lies inside that folder once the link '/src/jira-ticket/proj/.claude' is removed. " +
                "Deploying would overwrite or delete the files of the source. " +
                "Move the directory that project 'demo' deploys to out of the source folder, or turn off 'replace' for that deployment.",
        )
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
