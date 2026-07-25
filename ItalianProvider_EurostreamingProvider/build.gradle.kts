// use an integer for version numbers
version = 1

cloudstream {
    language = "it"
    // All of these properties are optional, you can safely remove them

    // description = "Lorem Ipsum"
    authors = listOf("Gian-Fr","Adippe")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "TvSeries"
    )

<<<<<<<< HEAD:EurostreamingProvider/build.gradle.kts

    iconUrl = "https://www.google.com/s2/favicons?domain=eurostreaming.money&sz=%size%"
========
    iconUrl = "https://www.google.com/s2/favicons?domain=starlive.xyz&sz=%size%"
>>>>>>>> parent of cc4d0aa (Disabled other providers for test):StarLiveProvider/build.gradle.kts
}
