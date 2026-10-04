package br.edu.unoesc.compraslocal.data

import br.edu.unoesc.compraslocal.data.entity.CategoryEntity

object DatabaseSeeder {
    suspend fun seedIfNeeded(db: AppDatabase) {
        if (db.categoryDao().count() > 0) return
        db.categoryDao().insertAll(
            listOf(
                CategoryEntity(name = "Laticínios", keywords = "leite,iogurte,queijo,manteiga,requeijao,nata"),
                CategoryEntity(name = "Limpeza", keywords = "detergente,sabao,desinfetante,amaciante,alvejante,limpa"),
                CategoryEntity(name = "Açougue", keywords = "carne,frango,peixe,linguica,costela,alcatra,picanha"),
                CategoryEntity(name = "Hortifruti", keywords = "banana,maca,tomate,alface,cebola,batata,laranja"),
                CategoryEntity(name = "Padaria", keywords = "pao,bolo,biscoito,torrada,rosquinha"),
                CategoryEntity(name = "Bebidas", keywords = "agua,refrigerante,suco,cafe,cerveja,cha"),
                CategoryEntity(name = "Mercearia", keywords = "arroz,feijao,macarrao,oleo,acucar,sal,farinha"),
                CategoryEntity(name = "Higiene", keywords = "pasta,escova,shampoo,papel higienico,absorvente"),
            ),
        )
    }
}
