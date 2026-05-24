package id.rahmat.marketedge.domain.usecase

import id.rahmat.marketedge.domain.repository.MarketEdgeRepository

class GetMarketAssetsUseCase(private val repository: MarketEdgeRepository) {
    operator fun invoke() = repository.marketAssets()
}
