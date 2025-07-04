// use an integer for version numbers
version = 10


cloudstream {
    language = "id"
    // All of these properties are optional, you can safely remove them

    // description = "Lorem Ipsum"
    authors = listOf("Hexated", "TeKuma", "Ufsean")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "AnimeMovie",
        "Anime",
        "Movie",
        "OVA",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=otakupoi.org/neonime&sz=%size%"
}