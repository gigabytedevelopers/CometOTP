@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail.EntryThumbnails
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The thumbnail is stored in every entry by its enum constant name ("thumbnail" in the JSON), so
 * renaming, dropping or reordering a constant silently changes what existing users see. This pins
 * the full list, in order, together with the drawable each one shows.
 */
class EntryThumbnailTest {

    @Test
    fun constantNamesOrderAndResourcesAreUnchanged() {
        val actual = EntryThumbnails.values().map { it.name to it.resource }
        assertEquals(EXPECTED.size, actual.size)
        for (i in EXPECTED.indices)
            assertEquals("constant #$i", EXPECTED[i], actual[i])
    }

    @Test
    fun valueOfIgnoreCaseMatchesStoredAndHandTypedNames() {
        assertEquals(EntryThumbnails.GitHub, EntryThumbnails.valueOfIgnoreCase("GitHub"))
        assertEquals(EntryThumbnails.Amazon, EntryThumbnails.valueOfIgnoreCase("amazon"))
        assertEquals(EntryThumbnails.AmazonWebServices, EntryThumbnails.valueOfIgnoreCase("AMAZONWEBSERVICES"))
        // Dots are dropped before the lookup, so an issuer such as "web.de" finds WebDe.
        assertEquals(EntryThumbnails.WebDe, EntryThumbnails.valueOfIgnoreCase("web.de"))
        assertEquals(EntryThumbnails.Default, EntryThumbnails.valueOfIgnoreCase("default"))
    }

    @Test
    fun valueOfIgnoreCaseRejectsUnknownAndNull() {
        assertThrows(IllegalArgumentException::class.java) { EntryThumbnails.valueOfIgnoreCase(null) }
        // On a device this is the IllegalArgumentException; on the JVM the android.util.Log call
        // just before it throws first. Either way the caller's catch falls back to Default.
        assertThrows(RuntimeException::class.java) { EntryThumbnails.valueOfIgnoreCase("NoSuchService") }
    }

    @Test
    fun valueOfFuzzyFindsAWholeWordAnywhereInTheIssuer() {
        assertEquals(EntryThumbnails.GitHub, EntryThumbnails.valueOfFuzzy("my GitHub login"))
        assertEquals(EntryThumbnails.Dropbox, EntryThumbnails.valueOfFuzzy("DROPBOX (work)"))
        assertThrows(IllegalArgumentException::class.java) { EntryThumbnails.valueOfFuzzy(null) }
        assertThrows(IllegalArgumentException::class.java) { EntryThumbnails.valueOfFuzzy("zzzz qqqq") }
    }

    /**
     * With several names in the issuer the one mentioned first wins, every time. (The patterns
     * used to live in a HashMap keyed by Pattern, which hashes by identity, so the winner changed
     * from one process to the next.)
     */
    @Test
    fun valueOfFuzzyPrefersTheNameMentionedFirst() {
        assertEquals(EntryThumbnails.Steam, EntryThumbnails.valueOfFuzzy("Steam Wallet"))
        assertEquals(EntryThumbnails.Wallet, EntryThumbnails.valueOfFuzzy("Wallet for Steam"))
        assertEquals(EntryThumbnails.Apple, EntryThumbnails.valueOfFuzzy("Apple School Manager"))
        assertEquals(EntryThumbnails.Microsoft, EntryThumbnails.valueOfFuzzy("Microsoft Office"))
        assertEquals(EntryThumbnails.Office, EntryThumbnails.valueOfFuzzy("Office by Microsoft"))
        assertEquals(EntryThumbnails.Google, EntryThumbnails.valueOfFuzzy("Google Email Privacy"))
        assertEquals(EntryThumbnails.Privacy, EntryThumbnails.valueOfFuzzy("my privacy, google and email"))
    }

    /** "Default" is not a service, so a real service in the same issuer takes precedence. */
    @Test
    fun valueOfFuzzyNeverPicksDefaultOverARealService() {
        assertEquals(EntryThumbnails.Google, EntryThumbnails.valueOfFuzzy("Default Google"))
        assertThrows(IllegalArgumentException::class.java) { EntryThumbnails.valueOfFuzzy("default account") }
    }

    /** The exact-name lookup runs before the fuzzy one, so an exact name still wins. */
    @Test
    fun anExactNameStillWinsOverFuzzyMatches() {
        fun thumbnailFor(issuer: String) = Entry(Entry.OTPType.TOTP, "JBSWY3DPEHPK3PXP", 30, 6, issuer, "l",
            HashAlgorithm.SHA1, mutableListOf()).thumbnail
        assertEquals(EntryThumbnails.MicrosoftTeams, thumbnailFor("MicrosoftTeams"))
        assertEquals(EntryThumbnails.AmazonWebServices, thumbnailFor("amazonwebservices"))
        assertEquals(EntryThumbnails.WebDe, thumbnailFor("web.de"))
        assertEquals(EntryThumbnails.Default, thumbnailFor("Default"))
        assertEquals(EntryThumbnails.Steam, thumbnailFor("Steam Wallet"))
        assertEquals(EntryThumbnails.Default, thumbnailFor("default account"))
    }

    /** Names written apart, with punctuation or in another case find the constant they spell. */
    @Test
    fun forIssuerIgnoresSpacesAndPunctuation() {
        assertEquals(EntryThumbnails.ProtonMail, EntryThumbnails.forIssuer("Proton Mail"))
        assertEquals(EntryThumbnails.EpicGames, EntryThumbnails.forIssuer("Epic Games"))
        assertEquals(EntryThumbnails.AmazonWebServices, EntryThumbnails.forIssuer("Amazon Web Services"))
        assertEquals(EntryThumbnails.WebDe, EntryThumbnails.forIssuer("web.de"))
        assertEquals(EntryThumbnails.ProtonMail, EntryThumbnails.forIssuer("proton-mail"))
        // Also inside a longer issuer, where the longer match wins at the same position.
        assertEquals(EntryThumbnails.AmazonWebServices, EntryThumbnails.forIssuer("Amazon Web Services (prod)"))
        assertEquals(EntryThumbnails.MicrosoftTeams, EntryThumbnails.forIssuer("Microsoft Teams"))
    }

    /** Names that start with a digit, which the constants spell out, and short names. */
    @Test
    fun forIssuerKnowsOtherNamesServicesGoBy() {
        assertEquals(EntryThumbnails.OnePassword, EntryThumbnails.forIssuer("1Password"))
        assertEquals(EntryThumbnails.OneAndOne, EntryThumbnails.forIssuer("1&1"))
        assertEquals(EntryThumbnails.FiveHundredPX, EntryThumbnails.forIssuer("500px"))
        assertEquals(EntryThumbnails.TwentyThreeAndMe, EntryThumbnails.forIssuer("23andMe"))
        assertEquals(EntryThumbnails.AmazonWebServices, EntryThumbnails.forIssuer("AWS"))
        assertEquals(EntryThumbnails.AmazonWebServices, EntryThumbnails.forIssuer("AWS Console"))
        assertEquals(EntryThumbnails.Microsoft, EntryThumbnails.forIssuer("Outlook.com"))
        assertEquals(EntryThumbnails.OnePassword, EntryThumbnails.forIssuer("1Password Business"))
    }

    @Test
    fun forIssuerFallsBackToTheInitialsTile() {
        assertEquals(EntryThumbnails.Default, EntryThumbnails.forIssuer(null))
        assertEquals(EntryThumbnails.Default, EntryThumbnails.forIssuer("  "))
        assertEquals(EntryThumbnails.Default, EntryThumbnails.forIssuer("zzzz qqqq"))
        // Words written together only match the whole name: "web design" is not web.de.
        assertEquals(EntryThumbnails.Default, EntryThumbnails.forIssuer("web design"))
    }

    /**
     * Entries keep the thumbnail chosen when they were added, so those still on their initials
     * tile are matched again once the matching improves; tiles set up by hand stay.
     */
    @Test
    fun rematchGivesUnmatchedEntriesTheirIconButKeepsChosenTiles() {
        fun entry(issuer: String, thumbnail: EntryThumbnails = EntryThumbnails.Default) =
            Entry(Entry.OTPType.TOTP, "JBSWY3DPEHPK3PXP", 30, 6, issuer, "l", HashAlgorithm.SHA1, mutableListOf())
                .also { it.thumbnail = thumbnail }

        val unmatched = entry("Proton Mail")
        assertEquals(true, unmatched.rematchThumbnail())
        assertEquals(EntryThumbnails.ProtonMail, unmatched.thumbnail)

        val withInitials = entry("Proton Mail").also { it.initials = "PM" }
        assertEquals(false, withInitials.rematchThumbnail())
        assertEquals(EntryThumbnails.Default, withInitials.thumbnail)

        val withColour = entry("Proton Mail").also { it.badgeColor = 0xFF2196F3.toInt() }
        assertEquals(false, withColour.rematchThumbnail())

        // An icon picked already, even another one, is never replaced.
        val picked = entry("Proton Mail", EntryThumbnails.GitHub)
        assertEquals(false, picked.rematchThumbnail())
        assertEquals(EntryThumbnails.GitHub, picked.thumbnail)

        // Nothing to match: stays on its tile, unchanged.
        val unknown = entry("Some Local Service")
        assertEquals(false, unknown.rematchThumbnail())
        assertEquals(EntryThumbnails.Default, unknown.thumbnail)
    }

    /** The icons added from Simple Icons, found by the names services are written with. */
    @Test
    fun forIssuerFindsTheAddedIcons() {
        assertEquals(EntryThumbnails.TikTok, EntryThumbnails.forIssuer("TikTok"))
        assertEquals(EntryThumbnails.BankOfAmerica, EntryThumbnails.forIssuer("Bank of America"))
        assertEquals(EntryThumbnails.EBay, EntryThumbnails.forIssuer("eBay"))
        assertEquals(EntryThumbnails.BookingCom, EntryThumbnails.forIssuer("Booking.com"))
        assertEquals(EntryThumbnails.FlyIo, EntryThumbnails.forIssuer("Fly.io"))
        assertEquals(EntryThumbnails.KoFi, EntryThumbnails.forIssuer("Ko-fi"))
        assertEquals(EntryThumbnails.X, EntryThumbnails.forIssuer("X"))
        assertEquals(EntryThumbnails.X, EntryThumbnails.forIssuer("X (Twitter)"))
        assertEquals(EntryThumbnails.Twitter, EntryThumbnails.forIssuer("Twitter"))
        // Proton's own logo, while Proton Mail keeps its icon.
        assertEquals(EntryThumbnails.Proton, EntryThumbnails.forIssuer("Proton Pass"))
        assertEquals(EntryThumbnails.ProtonMail, EntryThumbnails.forIssuer("Proton Mail"))
        assertEquals(EntryThumbnails.Ngrok, EntryThumbnails.forIssuer("ngrok"))
        assertEquals(EntryThumbnails.Payoneer, EntryThumbnails.forIssuer("Payoneer"))
    }

    /** The icons taken from the services' own sites, found by the names their accounts carry. */
    @Test
    fun forIssuerFindsTheBrandSiteIcons() {
        for (issuer in listOf("Airtm", "Bank78", "Blockradar", "Chowdeck", "Dojah", "Finecore", "HostAfrica", "Korapay",
                "Miden"))
            assertEquals(issuer, EntryThumbnails.valueOf(issuer), EntryThumbnails.forIssuer(issuer))
        assertEquals(EntryThumbnails.Korapay, EntryThumbnails.forIssuer("Kora"))
        assertEquals(EntryThumbnails.Maplerad, EntryThumbnails.forIssuer("Maplerad Dashboard"))
        // Globba's own icon for Globba ID, GlobbaPay's for GlobbaPay.
        assertEquals(EntryThumbnails.Globba, EntryThumbnails.forIssuer("Globba ID"))
        assertEquals(EntryThumbnails.GlobbaPay, EntryThumbnails.forIssuer("GlobbaPay (Business)"))
    }

    /** Everyday words and very short names only match the whole issuer, not a word inside it. */
    @Test
    fun everydayWordNamesOnlyMatchTheWholeIssuer() {
        assertEquals(EntryThumbnails.Square, EntryThumbnails.forIssuer("Square"))
        // Square Enix keeps its own icon rather than Square's, and an unknown one gets none.
        assertEquals(EntryThumbnails.SquareEnix, EntryThumbnails.forIssuer("Square Enix"))
        assertEquals(EntryThumbnails.Default, EntryThumbnails.forIssuer("Square One Credit Union"))
        assertEquals(EntryThumbnails.Ring, EntryThumbnails.forIssuer("ring"))
        assertEquals(EntryThumbnails.Default, EntryThumbnails.forIssuer("Ring Central"))
        assertEquals(EntryThumbnails.Default, EntryThumbnails.forIssuer("Company X"))
        assertEquals(EntryThumbnails.Default, EntryThumbnails.forIssuer("Medium Bank"))
    }

    private companion object {
        val EXPECTED = listOf(
            "Default" to R.mipmap.ic_launcher_round,
            "OneAndOne" to R.drawable.thumb_1and1,
            "OneBlu" to R.drawable.thumb_1blu,
            "OnePassword" to R.drawable.thumb_1password,
            "OneXBet" to R.drawable.thumb_1xbet,
            "TwentyThreeAndMe" to R.drawable.thumb_23andme,
            "FiveHundredPX" to R.drawable.thumb_500px,
            "Activision" to R.drawable.thumb_activision,
            "Adafruit" to R.drawable.thumb_adafruit,
            "AdGuard" to R.drawable.thumb_adguard,
            "Adobe" to R.drawable.thumb_adobe,
            "AirBNB" to R.drawable.thumb_airbnb,
            "Airbrake" to R.drawable.thumb_airbrake,
            "AirTable" to R.drawable.thumb_airtable,
            "Airtm" to R.drawable.thumb_airtm,
            "AirVPN" to R.drawable.thumb_airvpn,
            "Algolia" to R.drawable.thumb_algolia,
            "AliExpress" to R.drawable.thumb_ali_express,
            "AllegroPl" to R.drawable.thumb_allegropl,
            "Amazon" to R.drawable.thumb_amazon,
            "AmazonWebServices" to R.drawable.thumb_amazonwebservices,
            "AmericanExpress" to R.drawable.thumb_american_express,
            "AngelList" to R.drawable.thumb_angellist,
            "AnimeBytes" to R.drawable.thumb_animebytes,
            "Anonaddy" to R.drawable.thumb_anonaddy,
            "Anthropic" to R.drawable.thumb_anthropic,
            "AnyDesk" to R.drawable.thumb_anydesk,
            "Apache" to R.drawable.thumb_apache,
            "Apple" to R.drawable.thumb_apple,
            "AppVeyor" to R.drawable.thumb_appveyor,
            "ArchLinux" to R.drawable.thumb_archlinux,
            "ArenaNet" to R.drawable.thumb_arenanet,
            "Aruba" to R.drawable.thumb_aruba,
            "Asana" to R.drawable.thumb_asana,
            "AtlanticNet" to R.drawable.thumb_atlantic_net,
            "Atlassian" to R.drawable.thumb_atlassian,
            "Auth0" to R.drawable.thumb_auth0,
            "Autodesk" to R.drawable.thumb_autodesk,
            "AutoDNS" to R.drawable.thumb_autodns,
            "AVM" to R.drawable.thumb_avm,
            "Babiato" to R.drawable.thumb_babiato,
            "Backblaze" to R.drawable.thumb_backblaze,
            "Bank78" to R.drawable.thumb_bank78,
            "BankOfAmerica" to R.drawable.thumb_bank_of_america,
            "Barter" to R.drawable.thumb_barter,
            "BattleNet" to R.drawable.thumb_battlenet,
            "BestBuy" to R.drawable.thumb_bestbuy,
            "Betterment" to R.drawable.thumb_betterment,
            "Binance" to R.drawable.thumb_binance,
            "BitBucket" to R.drawable.thumb_bitbucket,
            "Bitcoin" to R.drawable.thumb_bitcoin,
            "Bitdefender" to R.drawable.thumb_bitdefender,
            "Bitfinex" to R.drawable.thumb_bitfinex,
            "Bitget" to R.drawable.thumb_bitget,
            "Bitmex" to R.drawable.thumb_bitmex,
            "Bitpanda" to R.drawable.thumb_bitpanda,
            "BitGo" to R.drawable.thumb_bitgo,
            "BitPay" to R.drawable.thumb_bitpay,
            "Bitstamp" to R.drawable.thumb_bitstamp,
            "Bittrex" to R.drawable.thumb_bittrex,
            "Bitwala" to R.drawable.thumb_bitwala,
            "Bitwarden" to R.drawable.thumb_bitwarden,
            "Blacknight" to R.drawable.thumb_blacknight,
            "BlockchainInfo" to R.drawable.thumb_blockchain_info,
            "BlockFi" to R.drawable.thumb_blockfi,
            "Blockradar" to R.drawable.thumb_blockradar,
            "Bluesky" to R.drawable.thumb_bluesky,
            "BookingCom" to R.drawable.thumb_booking_com,
            "Box" to R.drawable.thumb_box,
            "Boxcryptor" to R.drawable.thumb_boxcryptor,
            "Braintrust" to R.drawable.thumb_braintrust,
            "BraveRewards" to R.drawable.thumb_braverewards,
            "Brevo" to R.drawable.thumb_brevo,
            "Bugcrowd" to R.drawable.thumb_bugcrowd,
            "Busha" to R.drawable.thumb_busha,
            "Bybit" to R.drawable.thumb_bybit,
            "CashApp" to R.drawable.thumb_cash_app,
            "Celsius" to R.drawable.thumb_celsius,
            "CEXio" to R.drawable.thumb_cexio,
            "Charles_Schwab" to R.drawable.thumb_charles_schwab,
            "Chase" to R.drawable.thumb_chase,
            "Chowdeck" to R.drawable.thumb_chowdeck,
            "ChurchTools" to R.drawable.thumb_church_tools,
            "CircleCi" to R.drawable.thumb_circleci,
            "Cisco" to R.drawable.thumb_cisco,
            "Citrix" to R.drawable.thumb_citrix,
            "ClickUp" to R.drawable.thumb_clickup,
            "CloudDownload" to R.drawable.thumb_cloud_download,
            "Cloudflare" to R.drawable.thumb_cloudflare,
            "Cloudinary" to R.drawable.thumb_cloudinary,
            "Clubhouse" to R.drawable.thumb_clubhouse,
            "Cobranded" to R.drawable.thumb_cobranded,
            "Codeberg" to R.drawable.thumb_codeberg,
            "Codecov" to R.drawable.thumb_codecov,
            "CodeFortyTwo" to R.drawable.thumb_code42,
            "Codegiant" to R.drawable.thumb_codegiant,
            "Coinbase" to R.drawable.thumb_coinbase,
            "CoinDCX" to R.drawable.thumb_coindcx,
            "CoinJar" to R.drawable.thumb_coinjar,
            "ComputerBase" to R.drawable.thumb_computerbase,
            "Confluence" to R.drawable.thumb_confluence,
            "ConnectWiseManage" to R.drawable.thumb_connectwise_manage,
            "Contabo" to R.drawable.thumb_contabo,
            "Coursera" to R.drawable.thumb_coursera,
            "Cowrywise" to R.drawable.thumb_cowrywise,
            "CozyCloud" to R.drawable.thumb_cozycloud,
            "cPanel" to R.drawable.thumb_cpanel,
            "Crowdin" to R.drawable.thumb_crowdin,
            "CrowdSupply" to R.drawable.thumb_crowd_supply,
            "CryptoCom" to R.drawable.thumb_crypto_com,
            "CryptoComDeFiWallet" to R.drawable.thumb_crypto_com_defi_wallet,
            "CTemplar" to R.drawable.thumb_ctemplar,
            "Cyon" to R.drawable.thumb_cyon,
            "Dashlane" to R.drawable.thumb_dashlane,
            "Datadog" to R.drawable.thumb_datadog,
            "Debian" to R.drawable.thumb_debian,
            "Degiro" to R.drawable.thumb_degiro,
            "Denic" to R.drawable.thumb_denic,
            "Deribit" to R.drawable.thumb_deribit,
            "Diaspora" to R.drawable.thumb_diaspora,
            "Digidentity" to R.drawable.thumb_digidentity,
            "DigitalOcean" to R.drawable.thumb_digital_ocean,
            "Digitec" to R.drawable.thumb_digitec,
            "Directus" to R.drawable.thumb_directus,
            "Discogs" to R.drawable.thumb_discogs,
            "Discord" to R.drawable.thumb_discord,
            "Discourse" to R.drawable.thumb_discourse,
            "Disroot" to R.drawable.thumb_disroot,
            "Docker" to R.drawable.thumb_docker,
            "DocuSign" to R.drawable.thumb_docusign,
            "Dojah" to R.drawable.thumb_dojah,
            "DreamHost" to R.drawable.thumb_dreamhost,
            "Dropbox" to R.drawable.thumb_dropbox,
            "Drupal" to R.drawable.thumb_drupal,
            "Duolingo" to R.drawable.thumb_duolingo,
            "EA" to R.drawable.thumb_ea,
            "EBay" to R.drawable.thumb_e_bay,
            "Ecloud" to R.drawable.thumb_ecloud,
            "ElectronicArts" to R.drawable.thumb_electronic_arts,
            "Email" to R.drawable.thumb_email,
            "Eneba" to R.drawable.thumb_eneba,
            "Enpass" to R.drawable.thumb_enpass,
            "Envato" to R.drawable.thumb_envato,
            "EpicGames" to R.drawable.thumb_epic_games,
            "ESEA" to R.drawable.thumb_esea,
            "Etsy" to R.drawable.thumb_etsy,
            "Eveonline" to R.drawable.thumb_eveonline,
            "Evernote" to R.drawable.thumb_evernote,
            "ExpressVPN" to R.drawable.thumb_express_vpn,
            "Facebook" to R.drawable.thumb_facebook,
            "FACEIT" to R.drawable.thumb_faceit,
            "Fanatical" to R.drawable.thumb_fanatical,
            "Fastmail" to R.drawable.thumb_fastmail,
            "FidelityInvestments" to R.drawable.thumb_fidelity_investments,
            "Figma" to R.drawable.thumb_figma,
            "Finecore" to R.drawable.thumb_finecore,
            "Fingerprint" to R.drawable.thumb_fingerprint,
            "Finnair" to R.drawable.thumb_finnair,
            "Firebase" to R.drawable.thumb_firebase,
            "Fireblocks" to R.drawable.thumb_fireblocks,
            "Firefox" to R.drawable.thumb_firefox,
            "Fitbit" to R.drawable.thumb_fitbit,
            "Flight" to R.drawable.thumb_flight_takeoff,
            "Floatplane" to R.drawable.thumb_floatplane,
            "FluidFinance" to R.drawable.thumb_fluid_finance,
            "FlyIo" to R.drawable.thumb_fly_io,
            "Friendica" to R.drawable.thumb_friendica,
            "Fritz" to R.drawable.thumb_fritz,
            "FTX" to R.drawable.thumb_ftx,
            "Galaxus" to R.drawable.thumb_galaxus,
            "Gamepad" to R.drawable.thumb_gamepad,
            "Gandi" to R.drawable.thumb_gandi,
            "Garmin" to R.drawable.thumb_garmin,
            "GateIo" to R.drawable.thumb_gate_io,
            "Geegpay" to R.drawable.thumb_geegpay,
            "Gemini" to R.drawable.thumb_gemini,
            "Ghost" to R.drawable.thumb_ghost,
            "GigabyteDevelopers" to R.drawable.thumb_gigabyte_developers,
            "Git" to R.drawable.thumb_git,
            "Gitea" to R.drawable.thumb_gitea,
            "GitHub" to R.drawable.thumb_github,
            "GitLab" to R.drawable.thumb_gitlab,
            "Globba" to R.drawable.thumb_globba,
            "GlobbaPay" to R.drawable.thumb_globba_pay,
            "GMX" to R.drawable.thumb_gmx,
            "Go54" to R.drawable.thumb_go54,
            "GoatCounter" to R.drawable.thumb_goatcounter,
            "GoDaddy" to R.drawable.thumb_godaddy,
            "Gogs" to R.drawable.thumb_gogs,
            "Google" to R.drawable.thumb_google,
            "GovUK" to R.drawable.thumb_gov_uk,
            "Grafana" to R.drawable.thumb_grafana,
            "Grammarly" to R.drawable.thumb_grammarly,
            "Greenhost" to R.drawable.thumb_greenhost,
            "Grey" to R.drawable.thumb_grey,
            "GTwoA" to R.drawable.thumb_g2a,
            "Gumroad" to R.drawable.thumb_gumroad,
            "HackerOne" to R.drawable.thumb_hackerone,
            "Heroku" to R.drawable.thumb_heroku,
            "Hetzner" to R.drawable.thumb_hetzner,
            "HitBTC" to R.drawable.thumb_hitbtc,
            "HMRC" to R.drawable.thumb_gov_uk,
            "HomeAssistant" to R.drawable.thumb_home_assistant,
            "HostAfrica" to R.drawable.thumb_host_africa,
            "HostingDE" to R.drawable.thumb_hosting_de,
            "Hostinger" to R.drawable.thumb_hostinger,
            "Hover" to R.drawable.thumb_hover,
            "Huawei" to R.drawable.thumb_huawei,
            "HubSpot" to R.drawable.thumb_hubspot,
            "HuggingFace" to R.drawable.thumb_hugging_face,
            "HumbleBundle" to R.drawable.thumb_humblebundle,
            "HurricaneElectric" to R.drawable.thumb_hurricane_electric,
            "IBM" to R.drawable.thumb_ibm,
            "Iconomi" to R.drawable.thumb_iconomi,
            "IFTTT" to R.drawable.thumb_ifttt,
            "ImmoScout24" to R.drawable.thumb_immo_scout24,
            "Improvmx" to R.drawable.thumb_improvmx,
            "Infomaniak" to R.drawable.thumb_infomaniak,
            "Instagram" to R.drawable.thumb_instagram,
            "Intercom" to R.drawable.thumb_intercom,
            "INWX" to R.drawable.thumb_inwx,
            "Itchio" to R.drawable.thumb_itchio,
            "Jagex" to R.drawable.thumb_jagex,
            "Jellyfin" to R.drawable.thumb_jellyfin,
            "JetBrains" to R.drawable.thumb_jetbrains,
            "Jira" to R.drawable.thumb_jira,
            "Joomla" to R.drawable.thumb_joomla,
            "KDE" to R.drawable.thumb_kde,
            "Keeper" to R.drawable.thumb_keeper,
            "Keycloak" to R.drawable.thumb_keycloak,
            "Keyhelp" to R.drawable.thumb_keyhelp,
            "KhanAcademy" to R.drawable.thumb_khan_academy,
            "Kick" to R.drawable.thumb_kick,
            "Kickstarter" to R.drawable.thumb_kickstarter,
            "KoFi" to R.drawable.thumb_ko_fi,
            "Korapay" to R.drawable.thumb_korapay,
            "Kraken" to R.drawable.thumb_kraken,
            "Kucoin" to R.drawable.thumb_kucoin,
            "LastPass" to R.drawable.thumb_lastpass,
            "LibreNMS" to R.drawable.thumb_librenms,
            "Lichess" to R.drawable.thumb_lichess,
            "LinkedIn" to R.drawable.thumb_linkedin,
            "Linode" to R.drawable.thumb_linode,
            "LinusTechTips" to R.drawable.thumb_linustechtips,
            "Liqui" to R.drawable.thumb_liqui,
            "LivelyMe" to R.drawable.thumb_livelyme,
            "Lobsters" to R.drawable.thumb_lobsters,
            "LocalBitcoins" to R.drawable.thumb_localbitcoins,
            "LocalMonero" to R.drawable.thumb_localmonero,
            "LoginGov" to R.drawable.thumb_login_gov,
            "LogMeIn" to R.drawable.thumb_logmein,
            "LuaDNS" to R.drawable.thumb_luadns,
            "Luno" to R.drawable.thumb_luno,
            "Lyft" to R.drawable.thumb_lyft,
            "Mailbox" to R.drawable.thumb_mailbox,
            "Mailchimp" to R.drawable.thumb_mailchimp,
            "Mailcow" to R.drawable.thumb_mailcow,
            "MailDE" to R.drawable.thumb_mail_de,
            "Mailgun" to R.drawable.thumb_mailgun,
            "Mailru" to R.drawable.thumb_mailru,
            "Malwaretips" to R.drawable.thumb_malwaretips,
            "Mangadex" to R.drawable.thumb_mangadex,
            "Manitu" to R.drawable.thumb_manitu,
            "Mapbox" to R.drawable.thumb_mapbox,
            "Maplerad" to R.drawable.thumb_maplerad,
            "Mastodon" to R.drawable.thumb_mastodon,
            "Matomo" to R.drawable.thumb_matomo,
            "Mattermost" to R.drawable.thumb_mattermost,
            "Mediawiki" to R.drawable.thumb_mediawiki,
            "Medium" to R.drawable.thumb_medium,
            "Mega" to R.drawable.thumb_mega,
            "MercadoLibre" to R.drawable.thumb_mercadolibre,
            "Mercury" to R.drawable.thumb_mercury,
            "Meta" to R.drawable.thumb_meta,
            "Microsoft" to R.drawable.thumb_microsoft,
            "MicrosoftTeams" to R.drawable.thumb_microsoft_teams,
            "Miden" to R.drawable.thumb_miden,
            "Migadu" to R.drawable.thumb_migadu,
            "Migros" to R.drawable.thumb_migros,
            "Mint" to R.drawable.thumb_mint,
            "Miraheze" to R.drawable.thumb_miraheze,
            "Miro" to R.drawable.thumb_miro,
            "Mixer" to R.drawable.thumb_mixer,
            "MOneFinance" to R.drawable.thumb_m1finance,
            "MongoDB" to R.drawable.thumb_mongodb,
            "Monzo" to R.drawable.thumb_monzo,
            "Mullvad" to R.drawable.thumb_mullvad,
            "MVPSnet" to R.drawable.thumb_mvpsnet,
            "MyGovAU" to R.drawable.thumb_my_gov_au,
            "N26" to R.drawable.thumb_n26,
            "NameCheap" to R.drawable.thumb_namecheap,
            "NameCom" to R.drawable.thumb_namecom,
            "NameSilo" to R.drawable.thumb_namesilo,
            "NAS" to R.drawable.thumb_nas,
            "Netcup" to R.drawable.thumb_netcup,
            "Netlify" to R.drawable.thumb_netlify,
            "NewEgg" to R.drawable.thumb_newegg,
            "NewRelic" to R.drawable.thumb_new_relic,
            "Nexo" to R.drawable.thumb_nexo,
            "NextCloud" to R.drawable.thumb_nextcloud,
            "NextDNS" to R.drawable.thumb_nextdns,
            "NexusMods" to R.drawable.thumb_nexusmods,
            "Ngrok" to R.drawable.thumb_ngrok,
            "NiceHash" to R.drawable.thumb_nicehash,
            "Nintendo" to R.drawable.thumb_nintendo,
            "Nord" to R.drawable.thumb_nord,
            "NordVPN" to R.drawable.thumb_nord_vpn,
            "NoStarchPress" to R.drawable.thumb_no_starch_press,
            "Notion" to R.drawable.thumb_notion,
            "NPM" to R.drawable.thumb_npm,
            "NTPPoolProject" to R.drawable.thumb_ntppoolproject,
            "NVIDIA" to R.drawable.thumb_nvidia,
            "Oculus" to R.drawable.thumb_oculus,
            "Office" to R.drawable.thumb_office,
            "Okex" to R.drawable.thumb_okex,
            "Okta" to R.drawable.thumb_okta,
            "OKX" to R.drawable.thumb_okx,
            "OnlineNet" to R.drawable.thumb_online,
            "Onlyfans" to R.drawable.thumb_onlyfans,
            "Onshape" to R.drawable.thumb_onshape,
            "OpenAI" to R.drawable.thumb_openai,
            "OpenVPN" to R.drawable.thumb_openvpn,
            "OpenVZ" to R.drawable.thumb_openvz,
            "Opera" to R.drawable.thumb_opera,
            "OPNsense" to R.drawable.thumb_opnsense,
            "ORCiD" to R.drawable.thumb_orcid,
            "OtterAI" to R.drawable.thumb_otter_ai,
            "Origin" to R.drawable.thumb_origin,
            "OVH" to R.drawable.thumb_ovh,
            "Packet" to R.drawable.thumb_packet,
            "PagerDuty" to R.drawable.thumb_pager_duty,
            "Papaki" to R.drawable.thumb_papaki,
            "Parsecgaming" to R.drawable.thumb_parsecgaming,
            "Passwordstate" to R.drawable.thumb_passwordstate,
            "Patreon" to R.drawable.thumb_patreon,
            "Payoneer" to R.drawable.thumb_payoneer,
            "PayPal" to R.drawable.thumb_paypal,
            "PaySafe" to R.drawable.thumb_paysafecard,
            "Paystack" to R.drawable.thumb_paystack,
            "Payza" to R.drawable.thumb_payza,
            "PCloud" to R.drawable.thumb_pcloud,
            "PeeringDB" to R.drawable.thumb_peeringdb,
            "Percona" to R.drawable.thumb_percona,
            "Phabricator" to R.drawable.thumb_phabricator,
            "PhilipsHue" to R.drawable.thumb_philips_hue,
            "phpMyAdmin" to R.drawable.thumb_phpmyadmin,
            "Pinterest" to R.drawable.thumb_pinterest,
            "Pixelfed" to R.drawable.thumb_pixelfed,
            "PlayStation" to R.drawable.thumb_playstation,
            "Pleroma" to R.drawable.thumb_pleroma,
            "Plesk" to R.drawable.thumb_plesk,
            "Plex" to R.drawable.thumb_plex,
            "Pluralsight" to R.drawable.thumb_pluralsight,
            "Plurk" to R.drawable.thumb_plurk,
            "POEditor" to R.drawable.thumb_poeditor,
            "Porkbun" to R.drawable.thumb_porkbun,
            "Posteo" to R.drawable.thumb_posteo,
            "Pretix" to R.drawable.thumb_pretix,
            "Prey" to R.drawable.thumb_prey,
            "Privacy" to R.drawable.thumb_privacy,
            "PrivateInternetAccess" to R.drawable.thumb_private_internet_access,
            "Proton" to R.drawable.thumb_proton,
            "ProtonMail" to R.drawable.thumb_protonmail,
            "ProtonVPN" to R.drawable.thumb_protonvpn,
            "Proxmox" to R.drawable.thumb_proxmox,
            "PulseHeberg" to R.drawable.thumb_pulseheberg,
            "Purse" to R.drawable.thumb_purse,
            "Pushover" to R.drawable.thumb_pushover,
            "PyPI" to R.drawable.thumb_pypi,
            "PythonAnywhere" to R.drawable.thumb_python_anywhere,
            "QNAP" to R.drawable.thumb_qnap,
            "Rackspace" to R.drawable.thumb_rackspace,
            "Railway" to R.drawable.thumb_railway,
            "Razer" to R.drawable.thumb_razer,
            "Reddit" to R.drawable.thumb_reddit,
            "RedHat" to R.drawable.thumb_redhat,
            "Redis" to R.drawable.thumb_redis,
            "Reloadly" to R.drawable.thumb_reloadly,
            "Render" to R.drawable.thumb_render,
            "Replit" to R.drawable.thumb_replit,
            "Revolut" to R.drawable.thumb_revolut,
            "Ring" to R.drawable.thumb_ring,
            "RiotGames" to R.drawable.thumb_riot_games,
            "RipeNNC" to R.drawable.thumb_ripe_ncc,
            "Ripio" to R.drawable.thumb_ripio,
            "Robinhood" to R.drawable.thumb_robinhood,
            "Roblox" to R.drawable.thumb_roblox,
            "RocketChat" to R.drawable.thumb_rocket_chat,
            "Rockstar" to R.drawable.thumb_rockstar,
            "Roundcube" to R.drawable.thumb_roundcube,
            "RSS" to R.drawable.thumb_rss,
            "RubyGems" to R.drawable.thumb_rubygems,
            "Samsung" to R.drawable.thumb_samsung,
            "SAP" to R.drawable.thumb_sap,
            "Saxobank" to R.drawable.thumb_saxobank,
            "Scaleway" to R.drawable.thumb_scaleway,
            "Sciebo" to R.drawable.thumb_sciebo,
            "Seafile" to R.drawable.thumb_seafile,
            "SelfWealth" to R.drawable.thumb_selfwealth,
            "School" to R.drawable.thumb_school,
            "Sentry" to R.drawable.thumb_sentry,
            "ServiceNow" to R.drawable.thumb_servicenow,
            "Sevdesk" to R.drawable.thumb_sevdesk,
            "Shopify" to R.drawable.thumb_shopify,
            "Signal" to R.drawable.thumb_signal,
            "SimpleLogin" to R.drawable.thumb_simple_login,
            "ShopperCom" to R.drawable.thumb_shopper_com,
            "Skrill" to R.drawable.thumb_skrill,
            "Slack" to R.drawable.thumb_slack,
            "Smartbill" to R.drawable.thumb_smartbill,
            "Smartschool" to R.drawable.thumb_smartschool,
            "Snapchat" to R.drawable.thumb_snapchat,
            "SOGo" to R.drawable.thumb_sogo,
            "Sony" to R.drawable.thumb_sony,
            "Sophos" to R.drawable.thumb_sophos,
            "SourceForge" to R.drawable.thumb_sourceforge,
            "Sourcehut" to R.drawable.thumb_sourcehut,
            "Square" to R.drawable.thumb_square,
            "SquareEnix" to R.drawable.thumb_squareenix,
            "Squarespace" to R.drawable.thumb_squarespace,
            "Stack" to R.drawable.thumb_stack,
            "Stake" to R.drawable.thumb_stake,
            "StandardNotes" to R.drawable.thumb_standardnotes,
            "StarCitizen" to R.drawable.thumb_starcitizen,
            "Steam" to R.drawable.thumb_steam,
            "STEPN" to R.drawable.thumb_stepn,
            "Strato" to R.drawable.thumb_strato,
            "Strava" to R.drawable.thumb_strava,
            "Stripe" to R.drawable.thumb_stripe,
            "Substack" to R.drawable.thumb_substack,
            "Sumologic" to R.drawable.thumb_sumologic,
            "Supabase" to R.drawable.thumb_supabase,
            "Sync" to R.drawable.thumb_sync,
            "Synology" to R.drawable.thumb_synology,
            "Tailscale" to R.drawable.thumb_tailscale,
            "TeaHub" to R.drawable.thumb_teahub,
            "TeamViewer" to R.drawable.thumb_teamviewer,
            "Telegram" to R.drawable.thumb_telegram,
            "Terminal" to R.drawable.thumb_terminal,
            "TerraformCloud" to R.drawable.thumb_terraform_cloud,
            "TeslaMotors" to R.drawable.thumb_tesla_motors,
            "Threads" to R.drawable.thumb_threads,
            "TikTok" to R.drawable.thumb_tik_tok,
            "Tildes" to R.drawable.thumb_tildes,
            "TMobile" to R.drawable.thumb_t_mobile,
            "TradingView" to R.drawable.thumb_tradingview,
            "TransIP" to R.drawable.thumb_transip,
            "TravisCI" to R.drawable.thumb_travis_ci,
            "Trello" to R.drawable.thumb_trello,
            "Tresorit" to R.drawable.thumb_tresorit,
            "Trezor" to R.drawable.thumb_trezor,
            "TruckersMP" to R.drawable.thumb_truckersmp,
            "TruthSocial" to R.drawable.thumb_truthsocial,
            "Tumblr" to R.drawable.thumb_tumblr,
            "TUBerlin" to R.drawable.thumb_tu_berlin,
            "TurboTax" to R.drawable.thumb_turbotax,
            "Tuta" to R.drawable.thumb_tuta,
            "Tutanota" to R.drawable.thumb_tutanota,
            "TUWien" to R.drawable.thumb_tuwien_ac_at,
            "Tweakers" to R.drawable.thumb_tweakers,
            "Twilio" to R.drawable.thumb_twilio,
            "Twitch" to R.drawable.thumb_twitch,
            "Twitter" to R.drawable.thumb_twitter,
            "Uber" to R.drawable.thumb_uber,
            "UbiquitiNetworks" to R.drawable.thumb_ubiquiti,
            "Ubisoft" to R.drawable.thumb_ubisoft,
            "UbuntuOne" to R.drawable.thumb_ubuntu_one,
            "Udemy" to R.drawable.thumb_udemy,
            "Ultimaker" to R.drawable.thumb_ultimaker,
            "Unity" to R.drawable.thumb_unity,
            "Uphold" to R.drawable.thumb_uphold,
            "UptimeRobot" to R.drawable.thumb_uptime_robot,
            "Upwork" to R.drawable.thumb_upwork,
            "USAA" to R.drawable.thumb_usaa,
            "VagrantCloud" to R.drawable.thumb_vagrant_cloud,
            "Venmo" to R.drawable.thumb_venmo,
            "Vercel" to R.drawable.thumb_vercel,
            "VexxHost" to R.drawable.thumb_vexxhost,
            "VK" to R.drawable.thumb_vk,
            "VMware" to R.drawable.thumb_vmware,
            "Vultr" to R.drawable.thumb_vultr,
            "Wallabag" to R.drawable.thumb_wallabag,
            "Wallet" to R.drawable.thumb_wallet,
            "WalletsOne" to R.drawable.thumb_wallets_one,
            "Wargaming" to R.drawable.thumb_wargaming,
            "Wasabi" to R.drawable.thumb_wasabi,
            "Wazir" to R.drawable.thumb_wazir,
            "WebDe" to R.drawable.thumb_web_de,
            "Weclapp" to R.drawable.thumb_weclapp,
            "WellsFargo" to R.drawable.thumb_wells_fargo,
            "WhatsApp" to R.drawable.thumb_whats_app,
            "Whogohost" to R.drawable.thumb_whogohost,
            "Werknemerloket" to R.drawable.thumb_werknemerloket,
            "Wikimedia" to R.drawable.thumb_wikimedia,
            "Wildduck" to R.drawable.thumb_wildduck,
            "Windscribe" to R.drawable.thumb_windscribe,
            "Wise" to R.drawable.thumb_wise,
            "Wix" to R.drawable.thumb_wix,
            "WooCommerce" to R.drawable.thumb_woocommerce,
            "Wordpress" to R.drawable.thumb_wordpress,
            "Workplace" to R.drawable.thumb_workplace,
            "X" to R.drawable.thumb_x,
            "XDADevelopers" to R.drawable.thumb_xda_developers,
            "XendFinance" to R.drawable.thumb_xend_finance,
            "Xero" to R.drawable.thumb_xero,
            "Xerox" to R.drawable.thumb_xerox,
            "Xing" to R.drawable.thumb_xing,
            "Yahoo" to R.drawable.thumb_yahoo,
            "YandexMoney" to R.drawable.thumb_yandex_money,
            "YouTube" to R.drawable.thumb_you_tube,
            "YoYoGames" to R.drawable.thumb_yoyogames,
            "Zapier" to R.drawable.thumb_zapier,
            "Zendesk" to R.drawable.thumb_zendesk,
            "ZeroTier" to R.drawable.thumb_zero_tier,
            "Zoho" to R.drawable.thumb_zoho,
            "Zoom" to R.drawable.thumb_zoom
        )
    }
}
