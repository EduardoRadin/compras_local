package br.edu.unoesc.compraslocal.domain

import br.edu.unoesc.compraslocal.data.entity.CategoryEntity
import java.text.Normalizer
import java.util.Locale

object CategoryClassifier {
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase(Locale("pt", "BR"))
            .trim()

    fun classify(description: String, categories: List<CategoryEntity>): Long? {
        val normalized = normalize(description)
        return categories.firstOrNull { category ->
            category.keywords.split(",").any { keyword ->
                val k = keyword.trim()
                k.isNotEmpty() && normalized.contains(k)
            }
        }?.id
    }
}
