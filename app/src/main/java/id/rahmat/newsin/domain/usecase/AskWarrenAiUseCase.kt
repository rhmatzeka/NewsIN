package id.rahmat.newsin.domain.usecase

import id.rahmat.newsin.domain.repository.NewsInRepository

class AskWarrenAiUseCase(private val repository: NewsInRepository) {
    operator fun invoke(query: String) = repository.answerFor(query)
}
