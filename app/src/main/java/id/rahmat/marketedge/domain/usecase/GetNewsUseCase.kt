package id.rahmat.marketedge.domain.usecase

import id.rahmat.marketedge.domain.repository.MarketEdgeRepository

class GetNewsUseCase(private val repository: MarketEdgeRepository) {
    operator fun invoke() = repository.topNews()
}
