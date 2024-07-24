package com.phodal.shirecore.search.tokenizer

interface RegexTokenizerOptions {
    val pattern: Regex?
    val discardEmpty: Boolean
    val gaps: Boolean?
}

open class RegexpTokenizer(opts: RegexTokenizerOptions? = null) : Tokenizer {
    var whitespacePattern = Regex("\\s+")
    var discardEmpty: Boolean = true
    private var _gaps: Boolean? = null

    init {
        val options = opts ?: object : RegexTokenizerOptions {
            override val pattern: Regex? = null
            override val discardEmpty: Boolean = true
            override val gaps: Boolean? = null
        }

        whitespacePattern = options.pattern ?: whitespacePattern
        discardEmpty = options.discardEmpty
        _gaps = options.gaps

        if (_gaps == null) {
            _gaps = true
        }
    }

    override fun tokenize(s: String): List<String> {
        val results: List<String>

        if (_gaps == true) {
            results = s.split(whitespacePattern)
            return if (discardEmpty) without(results, "", " ") else results
        } else {
            results = whitespacePattern.findAll(s).map { it.value }.toList()
            return results.ifEmpty { emptyList() }
        }
    }

    fun without(arr: List<String>, vararg values: String): List<String> {
        return arr.filter { it !in values }
    }
}

class WordTokenizer(options: RegexTokenizerOptions? = null) : RegexpTokenizer(options) {
    init {
        whitespacePattern = Regex("[^A-Za-zА-Яа-я0-9_]+")
    }
}