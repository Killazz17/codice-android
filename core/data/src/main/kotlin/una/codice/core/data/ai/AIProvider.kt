package una.codice.core.data.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proveedor central de estrategias de IA ([AIProvider]) que permite seleccionar
 * dinámicamente la estrategia de transcripción (Gemini, Google Cloud Vision, etc.).
 */
@Singleton
class AIProvider @Inject constructor(
    private val geminiAIStrategy: GeminiAIStrategy,
) {
    fun getStrategy(providerName: String = "gemini"): ImageTranscriptionStrategy {
        return when (providerName.lowercase()) {
            "gemini", "google_cloud" -> geminiAIStrategy
            else -> geminiAIStrategy
        }
    }
}
