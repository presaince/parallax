/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.discover

import android.content.Context
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.db.RecipeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Built-in recipes are the same JSON as user ones, loaded from assets and stored in the same table
 * with `source = builtin`. No privileged path, so the parser is exercised by both.
 */
class RecipeRepository(
    private val context: Context,
    private val db: ParallaxDb,
) {

    fun observe(): Flow<List<RecipeEntity>> = db.recipes().observeAll()

    /**
     * The row as recipes, kept current. A backup restored from Settings can add a recipe while
     * Bored is still composed behind it, so the chips follow the table rather than a first read.
     * A row that no longer parses is left out; it has already been rejected once.
     */
    fun observeRecipes(): Flow<List<Recipe>> = db.recipes().observeAll().map { rows ->
        rows.mapNotNull { row ->
            val source = if (row.source == "user") RecipeSource.USER else RecipeSource.BUILTIN
            when (val parsed = RecipeParser.parse(row.json, source)) {
                is RecipeResult.Ok -> parsed.recipe
                is RecipeResult.Err -> null
            }
        }
    }

    suspend fun ensureBuiltIns(): List<String> = withContext(Dispatchers.IO) {
        val problems = mutableListOf<String>()
        val names = mutableListOf<String>()
        for (asset in BUILT_INS) {
            val json = runCatching {
                context.assets.open("recipes/$asset").bufferedReader().use { it.readText() }
            }.getOrElse {
                problems += "$asset: missing from assets"
                continue
            }
            when (val parsed = RecipeParser.parse(json, RecipeSource.BUILTIN)) {
                is RecipeResult.Ok -> {
                    names += parsed.recipe.name
                    db.recipes().upsert(
                        RecipeEntity(
                            name = parsed.recipe.name,
                            json = json,
                            source = "builtin",
                            createdAt = 0L,
                        ),
                    )
                }
                is RecipeResult.Err -> problems += "$asset: ${parsed.problems.first()}"
            }
        }
        // A renamed built-in must not leave its old name sitting in the row forever.
        if (names.isNotEmpty()) db.recipes().pruneBuiltIns(names)
        problems
    }

    suspend fun all(): List<Recipe> = withContext(Dispatchers.IO) {
        db.recipes().all().mapNotNull { row ->
            when (val parsed = RecipeParser.parse(row.json)) {
                is RecipeResult.Ok -> parsed.recipe
                is RecipeResult.Err -> null
            }
        }
    }

    suspend fun find(name: String): Recipe? = all().firstOrNull { it.name == name }

    /** Import is the same validator as the built-ins, with `source = user`. */
    suspend fun import(json: String): RecipeResult = withContext(Dispatchers.IO) {
        when (val parsed = RecipeParser.parse(json, RecipeSource.USER)) {
            is RecipeResult.Ok -> {
                val recipe = parsed.recipe
                db.recipes().upsert(
                    RecipeEntity(
                        name = recipe.name,
                        json = json,
                        source = "user",
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                parsed
            }
            is RecipeResult.Err -> parsed
        }
    }

    /**
     * A built-in is the app's, not the reader's. The query refuses it by source as well, so a
     * mistaken name cannot remove a recipe the assets still ship.
     */
    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        db.recipes().deleteUser(name)
    }

    /** The reader's own recipes as raw JSON, for a backup to carry. Built-ins are in the app. */
    suspend fun userJsons(): List<String> = withContext(Dispatchers.IO) {
        db.recipes().all().filter { it.source == "user" }.map { it.json }
    }

    private companion object {
        val BUILT_INS = listOf(
            "longform.json",
            "short-break.json",
            "fresh.json",
            "deep-cut.json",
        )
    }
}
