package cz.cleanship.aitools.engine.services

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import cz.cleanship.aitools.engine.models.AgentManifest
import cz.cleanship.aitools.engine.models.PromptManifest
import cz.cleanship.aitools.engine.models.RulePackManifest
import kotlinx.serialization.decodeFromString
import java.io.File

class LoaderService {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
//            strictMode = false, // Do not allow unknown keys
        ),
    )

    fun loadAgent(file: File): AgentManifest {
        val content = file.readText()
        return yaml.decodeFromString(content)
    }

    fun loadPrompt(file: File): PromptManifest {
        val content = file.readText()
        return yaml.decodeFromString(content)
    }

    fun loadRulepack(file: File): RulePackManifest {
        val content = file.readText()
        return yaml.decodeFromString(content)
    }

    fun findYamlFiles(directory: File): List<File> = directory
        .walkTopDown()
        .filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }
        .toList()
}
