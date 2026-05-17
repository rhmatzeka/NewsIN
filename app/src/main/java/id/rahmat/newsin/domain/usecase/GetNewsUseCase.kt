package id.rahmat.newsin.domain.usecase

import id.rahmat.newsin.domain.repository.NewsInRepository

class GetNewsUseCase(private val repository: NewsInRepository) {
    operator fun invoke() = repository.topNews()
}
