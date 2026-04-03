package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.models.AgentManifest
import cz.cleanship.aitools.engine.models.FeatureManifest
import cz.cleanship.aitools.engine.models.PromptManifest
import cz.cleanship.aitools.engine.models.RulesetManifest
import cz.cleanship.aitools.engine.models.SkillManifest
import java.io.File

class ManifestService(private val rootDir: File) {
    private val loader = LoaderService()

    fun listAgents(): List<AgentManifest> {
        val dir = File(rootDir, "05_agents")
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

    fun listRulesets(): List<RulesetManifest> {
        val dir = File(rootDir, "01_rulesets")
        if (!dir.exists()) return emptyList()
        return loader.findYamlFiles(dir).map {
            loader.loadRuleset(it)
        }
    }

    fun listFeatures(): List<FeatureManifest> {
        val dir = File(rootDir, "02_features")
        if (!dir.exists()) return emptyList()
        return loader.findYamlFiles(dir).map {
            loader.loadFeature(it)
        }
    }

    fun listSkills(): List<SkillManifest> {
        val dir = File(rootDir, "04_skills")
        if (!dir.exists()) return emptyList()
        return loader.findYamlFiles(dir).map {
            loader.loadSkill(it)
        }
    }
}
