package ai.byak.app.domain.repository

import ai.byak.app.domain.model.GeneratedImage
import ai.byak.app.domain.model.ImageGenerationRequest
import ai.byak.app.domain.model.ImageProvider

interface ImageGenerationRepository {
    suspend fun discoverModels(provider: ImageProvider): Result<List<String>>
    suspend fun generate(request: ImageGenerationRequest): Result<GeneratedImage>
    suspend fun saveToGallery(image: GeneratedImage): Result<String>
}
