// use an integer for version numbers
version = 3


cloudstream {
    language = "es"
    // All of these properties are optional, you can safely remove them

    //description = "Lorem Ipsum"
    authors = listOf("redblacker8")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 0 // site playdede.in down, redirected to enlaces.ly
    tvTypes = listOf(
        "TvSeries",
        "Movie",
    )

    iconUrl = "https://playdede.in/public/assets/favicon.ico"
}