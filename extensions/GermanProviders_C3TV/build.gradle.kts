// use an integer for version numbers
version = 5


cloudstream {
    language = "de"
    // All of these properties are optional, you can safely remove them

    description = "Vorträge von media.ccc.de"
    authors = listOf("Bnyro")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Others"
    )

    iconUrl = "https://media.ccc.de/favicon-96x96.png"
}
