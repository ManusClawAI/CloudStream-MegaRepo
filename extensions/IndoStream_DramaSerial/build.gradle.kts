// use an integer for version numbers
version = 7


cloudstream {
    language = "id"
    // All of these properties are optional, you can safely remove them

     description = "Drama Serial, harus pake vpn"
     authors = listOf("Hexated", "TeKuma25")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "AsianDrama",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=tv3.dramaserial.id&sz=%size%"
}