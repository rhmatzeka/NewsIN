package id.rahmat.newsin.domain.usecase

import id.rahmat.newsin.domain.repository.NewsInRepository

class GetMarketAssetsUseCase(private val repository: NewsInRepository) {
    operator fun invoke() = repository.marketAssets()
}
