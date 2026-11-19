package com.example.tts

import java.io.File

/**
 * Model hyperparameters parsed from `config.yaml` and verified against `model.safetensors` tensor shapes.
 * Defaults reflect the `mehdi-hf/cilla-mini` (Kyutai Pocket-TTS 18.2M Persian recipe).
 */
data class PocketTtsConfig(
    val sampleRate: Int = 24_000,
    val frameRate: Float = 12.5f,
    val latentDim: Int = 64,
    val dModel: Int = 512,
    val numHeads: Int = 8,
    val numKvHeads: Int = 8,
    val numLayers: Int = 8,
    val dimFeedforward: Int = 2048,
    val ropeMaxPeriod: Float = 10_000f,
    val flowDim: Int = 512,
    val flowDepth: Int = 4,
    val capitalizeFirstLetter: Boolean = false,
    val appendTerminalPunctuation: Boolean = false,
    val padWithSpacesForShortInputs: Boolean = false,
) {
    val samplesPerFrame: Int
        get() = (sampleRate / frameRate).toInt().coerceAtLeast(1)

    companion object {
        fun load(configFile: File?, reader: SafetensorsReader?): PocketTtsConfig {
            var cfg = PocketTtsConfig()

            if (configFile != null && configFile.exists()) {
                val lines = configFile.readLines()
                for (rawLine in lines) {
                    val line = rawLine.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue
                    val colonIdx = line.indexOf(':')
                    if (colonIdx <= 0) continue
                    val key = line.substring(0, colonIdx).trim()
                    val value = line.substring(colonIdx + 1).trim().trim('"', '\'')
                    when (key) {
                        "sample_rate" -> value.toIntOrNull()?.let { cfg = cfg.copy(sampleRate = it) }
                        "frame_rate" -> value.toFloatOrNull()?.let { cfg = cfg.copy(frameRate = it) }
                        "ldim", "latent_dim", "dimension" -> {
                            value.toIntOrNull()?.let {
                                if (key == "ldim" || key == "latent_dim") cfg = cfg.copy(latentDim = it)
                            }
                        }
                        "d_model" -> value.toIntOrNull()?.let { cfg = cfg.copy(dModel = it) }
                        "num_heads" -> value.toIntOrNull()?.let { cfg = cfg.copy(numHeads = it) }
                        "num_kv_heads" -> value.toIntOrNull()?.let { cfg = cfg.copy(numKvHeads = it) }
                        "num_layers" -> value.toIntOrNull()?.let { cfg = cfg.copy(numLayers = it) }
                        "dim_feedforward" -> value.toIntOrNull()?.let { cfg = cfg.copy(dimFeedforward = it) }
                        "max_period" -> value.toFloatOrNull()?.let { cfg = cfg.copy(ropeMaxPeriod = it) }
                        "capitalize_first_letter" -> cfg = cfg.copy(capitalizeFirstLetter = value.toBoolean())
                        "append_terminal_punctuation" -> cfg = cfg.copy(appendTerminalPunctuation = value.toBoolean())
                        "pad_with_spaces_for_short_inputs" -> cfg = cfg.copy(padWithSpacesForShortInputs = value.toBoolean())
                    }
                }
            }

            // Override/verify structural dimensions directly from safetensors tensor shapes
            if (reader != null) {
                val layerIndices = reader.tensors.keys
                    .mapNotNull { Regex("""layers\.(\d+)\.""").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
                if (layerIndices.isNotEmpty()) {
                    val flowLmLayers = reader.tensors.keys
                        .filter { it.startsWith("flow_lm.") }
                        .mapNotNull { Regex("""layers\.(\d+)\.""").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
                    val inferredLayers = (flowLmLayers.maxOrNull() ?: layerIndices.maxOrNull() ?: 7) + 1
                    cfg = cfg.copy(numLayers = inferredLayers)
                }

                reader.findFirstKey(
                    "flow_lm.bos_emb",
                    "bos_emb",
                )?.let { bosKey ->
                    val shape = reader.tensors[bosKey]?.shape
                    if (shape != null && shape.isNotEmpty()) {
                        cfg = cfg.copy(latentDim = shape.last())
                    }
                }

                reader.findFirstKey(
                    "flow_lm.out_norm.weight",
                    "out_norm.weight",
                    "flow_lm.transformer.layers.0.norm1.weight",
                )?.let { normKey ->
                    val shape = reader.tensors[normKey]?.shape
                    if (shape != null && shape.isNotEmpty()) {
                        cfg = cfg.copy(dModel = shape.last())
                    }
                }
            }

            return cfg
        }
    }
}
