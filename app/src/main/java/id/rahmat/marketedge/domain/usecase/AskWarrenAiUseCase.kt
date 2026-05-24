package id.rahmat.marketedge.domain.usecase

import id.rahmat.marketedge.domain.repository.MarketEdgeRepository

class AskWarrenAiUseCase(private val repository: MarketEdgeRepository) {
    operator fun invoke(query: String) = repository.answerFor(query)
}
