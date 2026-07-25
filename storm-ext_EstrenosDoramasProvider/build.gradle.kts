// use an integer for version numbers
version = 4


cloudstream {
    language = "mx"
    // All of these properties are optional, you can safely remove them

    //description = "Lorem Ipsum"
    authors = listOf("Stormunblessed")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 0 // Down - domain hijacked
    tvTypes = listOf(
        "AsianDrama",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=estrenosdoramas.net&sz=%size%"
}