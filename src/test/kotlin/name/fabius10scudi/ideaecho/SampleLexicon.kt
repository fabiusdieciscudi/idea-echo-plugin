package name.fabius10scudi.ideaecho

/** The small Morph-it! style lexicon in the test resources, loaded once. */
object SampleLexicon {
    val lexicon: MorphLexicon by lazy {
        val stream = SampleLexicon::class.java.getResourceAsStream("morph-it-sample.txt")
            ?: error("morph-it-sample.txt not found in the test resources")
        stream.use { MorphLexicon.read(it) }
    }
}
