package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.models.AgentManifest
import cz.cleanship.aitools.engine.models.PromptManifest
import cz.cleanship.aitools.engine.models.RulePackManifest
import java.io.File

class ManifestService(private val rootDir: File) {
    private val loader = LoaderService()

    fun listAgents(): List<AgentManifest> {
        val dir = File(rootDir, "04_agents")
        if (!dir.exists()) return emptyList()
        return loader.findYamlFiles(dir).map {
            loader.loadAgent(it)
        }
    }

    fun listPrompts(): List<PromptManifest> {
        val dir = File(rootDir, "03_prompts")
        if (!dir.exists()) return emptyList()
        return loader.findYamlFiles(dir).map {
            loader.loadPrompt(it)
        }
    }

    fun listRulepacks(): List<RulePackManifest> {
        val dir = File(rootDir, "01_rulepacks")
        if (!dir.exists()) return emptyList()
        return loader.findYamlFiles(dir).map {
            loader.loadRulepack(it)
        }
    }
}
