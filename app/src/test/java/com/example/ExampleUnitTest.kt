package com.example

import com.example.core.model.DamanStyle
import com.example.core.model.NeckStyle
import com.example.core.model.PocketStyle
import com.example.core.model.SleeveStyle
import com.example.core.ui.DateFormatUtils
import com.example.data.backup.DarziBackupBundle
import com.example.data.backup.ShopBackupRepository
import com.example.data.backup.UrduSheetCsvParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun csvBackupBundleDecodesAndFormatsMissingInchesCleanly() {
    val json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
    }
    val sample = """
      {
        "schemaVersion": 1,
        "appId": "darzi_ledger",
        "exportedAtMillis": 1763830100000,
        "customers": [
          {"customerId":"csv_cust_001","shopId":"default_shop","name":"سراےڈی طارق صاحب","phone":"0300-7387915","normalizedPhone":"03007387915","customId":"No.4","createdAt":1763830001000,"updatedAt":1763830001000,"lastModifiedByDevice":"csv-import"}
        ],
        "measurements": [
          {"measurementId":"csv_meas_001","customerId":"csv_cust_001","kameezLambai":39.0,"tera":19.0,"bazu":24.0,"chati":46.0,"kamar":0.0,"gheraDaman":30.0,"galaCollar":17.5,"cuff":7.0,"shalwarLambai":0.0,"paincha":0.0,"gheraAsan":0.0,"createdAt":1763830002000,"updatedAt":1763830002000,"lastModifiedByDevice":"csv-import"}
        ]
      }
    """.trimIndent()

    val bundle = json.decodeFromString<DarziBackupBundle>(sample)
    assertEquals(ShopBackupRepository.APP_IDENTIFIER, bundle.appId)
    assertEquals(ShopBackupRepository.CURRENT_SCHEMA_VERSION, bundle.schemaVersion)
    assertEquals("No.4", bundle.customers.single().customId)
    assertTrue(bundle.measurements.single().isCurrent)
    assertEquals("Standard", bundle.measurements.single().label)
    assertEquals("—", DateFormatUtils.formatInches(bundle.measurements.single().shalwarLambai))
    assertEquals("39\"", DateFormatUtils.formatInches(bundle.measurements.single().kameezLambai))

    val assetFile = java.io.File("src/main/assets/google_sheet_customers_backup.json")
      .takeIf { it.exists() }
      ?: java.io.File("app/src/main/assets/google_sheet_customers_backup.json")
    assertTrue("Expected bundled asset file to exist", assetFile.exists())
    val fullBundle = json.decodeFromString<DarziBackupBundle>(assetFile.readText(Charsets.UTF_8))
    assertEquals(97, fullBundle.customers.size)
    assertEquals(97, fullBundle.measurements.size)
    assertEquals("No.4", fullBundle.customers.first().customId)
    assertEquals("0300-7387915", fullBundle.customers.first().phone)
    assertEquals("no100", fullBundle.customers.last().customId)
  }

  @Test
  fun sheet2BackupAssetValidatesAgainstFirestoreAndTailorSchema() {
    val json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
    }
    val assetFile = java.io.File("src/main/assets/google_sheet_400_backup.json")
      .takeIf { it.exists() }
      ?: java.io.File("app/src/main/assets/google_sheet_400_backup.json")
    assertTrue("Expected bundled Sheet 2 asset file to exist", assetFile.exists())

    val bundle = json.decodeFromString<DarziBackupBundle>(assetFile.readText(Charsets.UTF_8))
    assertEquals(ShopBackupRepository.APP_IDENTIFIER, bundle.appId)
    assertEquals(874, bundle.customers.size)
    assertEquals(873, bundle.measurements.size)

    val customerIds = bundle.customers.map { it.customerId }.toSet()
    assertEquals(874, customerIds.size)
    assertEquals(873, bundle.measurements.map { it.measurementId }.toSet().size)

    bundle.customers.forEach { c ->
      assertTrue(c.name.isNotBlank() && c.name.length <= 100)
      assertTrue(c.customId.length <= 40)
      assertTrue(c.phone.length <= 30)
      assertTrue(c.notes.length <= 500)
    }

    bundle.measurements.forEach { m ->
      assertTrue(m.customerId in customerIds)
      assertTrue("lambai > 0 for ${m.measurementId}", m.kameezLambai > 0.0 && m.kameezLambai <= 200.0)
      assertTrue("tera > 0 for ${m.measurementId}", m.tera > 0.0 && m.tera <= 200.0)
      assertTrue("bazu > 0 for ${m.measurementId}", m.bazu > 0.0 && m.bazu <= 200.0)
      assertTrue("chati > 0 for ${m.measurementId}", m.chati > 0.0 && m.chati <= 200.0)
      assertTrue("gheraDaman > 0 for ${m.measurementId}", m.gheraDaman > 0.0 && m.gheraDaman <= 200.0)
      assertTrue(m.galaCollar in 0.0..200.0)
      assertTrue(m.cuff in 0.0..200.0)
      assertTrue(m.shalwarLambai in 0.0..200.0)
      assertTrue(m.paincha in 0.0..200.0)
      assertTrue(m.shalwarGhair in 0.0..200.0)
      assertTrue(m.notes.length <= 500)
      assertTrue(m.rawInputsJson.length <= 500)
    }

    val measByCustomer = bundle.measurements.associateBy { it.customerId }
    val custById = bundle.customers.associateBy { it.customerId }

    // Reversed cuff/bazu: "9کف 22.5"
    val m98 = measByCustomer["csv2_cust_098"]!!
    assertEquals(22.5, m98.bazu, 0.01)
    assertEquals(9.0, m98.cuff, 0.01)
    assertEquals(SleeveStyle.KAF, m98.sleeveStyle)

    // Shifted kameez-only pocket row
    val m167 = measByCustomer["csv2_cust_167"]!!
    assertEquals(0.0, m167.shalwarLambai, 0.01)
    assertTrue(PocketStyle.ONE_SIDE in m167.pocketStyles)

    // Missing lambai row ("no121") preserved safely in customer notes
    val c21 = custById["csv2_cust_021"]!!
    assertEquals("no121", c21.customId)
    assertTrue(c21.notes.contains("ناپ نامکمل"))
    assertEquals(null, measByCustomer["csv2_cust_021"])

    // Phone extraction
    assertEquals("0302-0723974", custById["csv2_cust_088"]!!.phone)

    // Cuff 95 typo -> 9.5
    assertEquals(9.5, measByCustomer["csv2_cust_872"]!!.cuff, 0.01)
  }

  @Test
  fun urduCsvParserParsesFractionsStylesDualGheraAndPhones() {
    val json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
    }
    val csv = """
      no2,نام ,قمیض لمبائی,تیرا,آستین,گلا ,سینہ,گھیرا/دامن,شلوار,پانچہ,,فون نمبر,
      no.105,طارق صاحب,39.5 جیب,18,22.5 کف سوا 9,15.5 بین پونی,40 سینہ,23/22 گول,39 جیب,8,کمانڈو ڈبل سلائی,3007367918,
    """.trimIndent()

    val encoded = UrduSheetCsvParser.convertCsvToBackupJson(csv, json, nowMillis = 1763840000000L).getOrThrow()
    val bundle = json.decodeFromString<DarziBackupBundle>(encoded)
    assertEquals(1, bundle.customers.size)
    assertEquals(1, bundle.measurements.size)

    val cust = bundle.customers.single()
    assertEquals("no.105", cust.customId)
    assertEquals("0300-7367918", cust.phone)

    val meas = bundle.measurements.single()
    assertEquals(39.5, meas.kameezLambai, 0.01)
    assertEquals(22.5, meas.bazu, 0.01)
    assertEquals(9.25, meas.cuff, 0.01)
    assertEquals(NeckStyle.HALF_BAN, meas.neckStyle)
    assertEquals(DamanStyle.GOL, meas.damanStyle)
    assertEquals("23/22", meas.rawInputsMap()["GHERA_DAMAN"])
    assertTrue(PocketStyle.FRONT in meas.pocketStyles)
    assertTrue(PocketStyle.SHALWAR_POCKET in meas.pocketStyles)
  }

  @Test
  fun sheet3BackupAssetValidatesAgainstFirestoreAndTailorSchema() {
    val json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
    }
    val assetFile = java.io.File("src/main/assets/google_sheet_500_backup.json")
      .takeIf { it.exists() }
      ?: java.io.File("app/src/main/assets/google_sheet_500_backup.json")
    assertTrue("Expected bundled Sheet 3 asset file to exist", assetFile.exists())

    val bundle = json.decodeFromString<DarziBackupBundle>(assetFile.readText(Charsets.UTF_8))
    assertEquals(ShopBackupRepository.APP_IDENTIFIER, bundle.appId)
    assertEquals(573, bundle.customers.size)
    assertEquals(573, bundle.measurements.size)

    val customerIds = bundle.customers.map { it.customerId }.toSet()
    assertEquals(573, customerIds.size)
    assertEquals(573, bundle.measurements.map { it.measurementId }.toSet().size)

    bundle.customers.forEach { c ->
      assertTrue(c.name.isNotBlank() && c.name.length <= 100)
      assertTrue(c.customId.length <= 40)
      assertTrue(c.phone.length <= 30)
      assertTrue(c.notes.length <= 500)
    }

    bundle.measurements.forEach { m ->
      assertTrue(m.customerId in customerIds)
      assertTrue("lambai > 0 for ${m.measurementId}", m.kameezLambai > 0.0 && m.kameezLambai <= 200.0)
      assertTrue("tera > 0 for ${m.measurementId}", m.tera > 0.0 && m.tera <= 200.0)
      assertTrue("bazu > 0 for ${m.measurementId}", m.bazu > 0.0 && m.bazu <= 200.0)
      assertTrue("chati > 0 for ${m.measurementId}", m.chati > 0.0 && m.chati <= 200.0)
      assertTrue("gheraDaman > 0 for ${m.measurementId}", m.gheraDaman > 0.0 && m.gheraDaman <= 200.0)
      assertTrue(m.galaCollar in 0.0..200.0)
      assertTrue(m.cuff in 0.0..200.0)
      assertTrue(m.shalwarLambai in 0.0..200.0)
      assertTrue(m.paincha in 0.0..200.0)
      assertTrue(m.shalwarGhair in 0.0..200.0)
      assertTrue(m.notes.length <= 500)
      assertTrue(m.rawInputsJson.length <= 500)
    }

    val custByCustomId = bundle.customers.associateBy { it.customId }
    val measByCustomer = bundle.measurements.associateBy { it.customerId }

    // id1049: "14. کالر .5" -> 14.5 COLLAR
    val c1049 = custByCustomId["id1049"]!!
    assertEquals(14.5, measByCustomer[c1049.customerId]!!.galaCollar, 0.01)
    assertEquals(NeckStyle.COLLAR, measByCustomer[c1049.customerId]!!.neckStyle)

    // id1055: "22 کف5،۔8" -> bazu 22.0, cuff 8.5
    val c1055 = custByCustomId["id1055"]!!
    assertEquals(22.0, measByCustomer[c1055.customerId]!!.bazu, 0.01)
    assertEquals(8.5, measByCustomer[c1055.customerId]!!.cuff, 0.01)

    // id1167: "11-Dec" -> 11/12
    val c1167 = custByCustomId["id1167"]!!
    assertEquals(11.0, measByCustomer[c1167.customerId]!!.gheraDaman, 0.01)
    assertEquals("11/12", measByCustomer[c1167.customerId]!!.rawInputsMap()["GHERA_DAMAN"])

    // id1208: reversed cuff "سوا 9کف 5۔23" & dual phones
    val c1208 = custByCustomId["id1208"]!!
    assertEquals("0301-7420628", c1208.phone)
    assertTrue(c1208.notes.contains("0309-6781785"))
    assertEquals(23.5, measByCustomer[c1208.customerId]!!.bazu, 0.01)
    assertEquals(9.25, measByCustomer[c1208.customerId]!!.cuff, 0.01)

    // id1258: "418جیب" typo clamped safely to 41.0
    val c1258 = custByCustomId["id1258"]!!
    assertEquals(41.0, measByCustomer[c1258.customerId]!!.shalwarLambai, 0.01)

    // id1266: "5.21by20.5" -> 21.5/20.5
    val c1266 = custByCustomId["id1266"]!!
    assertEquals(21.5, measByCustomer[c1266.customerId]!!.gheraDaman, 0.01)
    assertEquals("21.5/20.5", measByCustomer[c1266.customerId]!!.rawInputsMap()["GHERA_DAMAN"])

    // id1270: "24کف2اینچ" -> bazu 24.0, cuff 0.0 + width note
    val c1270 = custByCustomId["id1270"]!!
    assertEquals(24.0, measByCustomer[c1270.customerId]!!.bazu, 0.01)
    assertEquals(0.0, measByCustomer[c1270.customerId]!!.cuff, 0.01)
    assertTrue(measByCustomer[c1270.customerId]!!.notes.contains("2 اینچ"))

    // id943: sewing note in phone col moved to measurement notes
    val c943 = custByCustomId["id943"]!!
    assertEquals("", c943.phone)
    assertTrue(measByCustomer[c943.customerId]!!.notes.contains("چاک پٹی کاچ"))
  }

  @Test
  fun urduCsvParserHandlesSheet3RtlDecimalsAndEdgeCases() {
    val json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
    }
    val csv = """
      کسٹمر آئی ڈی,نام ,قمیض لمبائی,تیرا,آستین,گلا ,سینہ,گھیرا/دامن,شلوار,پانچہ,,فون نمبر
      id1208,نزر ولد مرید لائل,جیب5۔40,5۔18,سوا 9کف 5۔23,14. کالر .5,40,5۔20؛5۔21,418جیب,5۔8,کمانڈو ڈبل سلائی,3017420628 0309 6781785
    """.trimIndent()

    val encoded = UrduSheetCsvParser.convertCsvToBackupJson(csv, json, nowMillis = 1763900000000L).getOrThrow()
    val bundle = json.decodeFromString<DarziBackupBundle>(encoded)
    val cust = bundle.customers.single()
    val meas = bundle.measurements.single()

    assertEquals("0301-7420628", cust.phone)
    assertTrue(cust.notes.contains("0309-6781785"))
    assertEquals(40.5, meas.kameezLambai, 0.01)
    assertEquals(18.5, meas.tera, 0.01)
    assertEquals(23.5, meas.bazu, 0.01)
    assertEquals(9.25, meas.cuff, 0.01)
    assertEquals(14.5, meas.galaCollar, 0.01)
    assertEquals(NeckStyle.COLLAR, meas.neckStyle)
    assertEquals(20.5, meas.gheraDaman, 0.01)
    assertEquals("20.5/21.5", meas.rawInputsMap()["GHERA_DAMAN"])
    assertEquals(41.0, meas.shalwarLambai, 0.01)
    assertEquals(8.5, meas.paincha, 0.01)
  }

  @Test
  fun sheet4BackupAssetValidatesAgainstFirestoreAndTailorSchema() {
    val json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
    }
    val assetFile = java.io.File("src/main/assets/google_sheet_600_backup.json")
      .takeIf { it.exists() }
      ?: java.io.File("app/src/main/assets/google_sheet_600_backup.json")
    assertTrue("Expected bundled Sheet 4 asset file to exist", assetFile.exists())

    val bundle = json.decodeFromString<DarziBackupBundle>(assetFile.readText(Charsets.UTF_8))
    assertEquals(ShopBackupRepository.APP_IDENTIFIER, bundle.appId)
    assertEquals(681, bundle.customers.size)
    assertEquals(648, bundle.measurements.size)

    val customerIds = bundle.customers.map { it.customerId }.toSet()
    assertEquals(681, customerIds.size)
    assertEquals(648, bundle.measurements.map { it.measurementId }.toSet().size)

    bundle.customers.forEach { c ->
      assertTrue(c.name.isNotBlank() && c.name.length <= 100)
      assertTrue(c.customId.length <= 40)
      assertTrue(c.phone.length <= 30)
      assertTrue(c.normalizedPhone.length <= 30)
      assertTrue(c.notes.length <= 500)
    }

    bundle.measurements.forEach { m ->
      assertTrue(m.customerId in customerIds)
      assertTrue("lambai > 0 for ${m.measurementId}", m.kameezLambai > 0.0 && m.kameezLambai <= 200.0)
      assertTrue("tera > 0 for ${m.measurementId}", m.tera > 0.0 && m.tera <= 200.0)
      assertTrue("bazu > 0 for ${m.measurementId}", m.bazu > 0.0 && m.bazu <= 200.0)
      assertTrue("chati > 0 for ${m.measurementId}", m.chati > 0.0 && m.chati <= 200.0)
      assertTrue("gheraDaman > 0 for ${m.measurementId}", m.gheraDaman > 0.0 && m.gheraDaman <= 200.0)
      assertTrue(m.galaCollar in 0.0..200.0)
      assertTrue(m.cuff in 0.0..200.0)
      assertTrue(m.shalwarLambai in 0.0..200.0)
      assertTrue(m.paincha in 0.0..200.0)
      assertTrue(m.shalwarGhair in 0.0..200.0)
      assertTrue(m.notes.length <= 500)
      assertTrue(m.rawInputsJson.length <= 500)
    }

    val custByCustomId = bundle.customers.associateBy { it.customId }
    val measByCustomer = bundle.measurements.associateBy { it.customerId }

    // no.2126: "May-38" -> 38.5
    val c2126 = custByCustomId["no.2126"]!!
    assertEquals(38.5, measByCustomer[c2126.customerId]!!.kameezLambai, 0.01)

    // no.1544: "آرمول 8 34 تیار سینہ" -> chati 34.0 (not 8.0)
    val c1544 = custByCustomId["no.1544"]!!
    assertEquals(34.0, measByCustomer[c1544.customerId]!!.chati, 0.01)

    // no.1547: "25کف5.9" -> bazu 25.0, cuff 9.5
    val c1547 = custByCustomId["no.1547"]!!
    assertEquals(25.0, measByCustomer[c1547.customerId]!!.bazu, 0.01)
    assertEquals(9.5, measByCustomer[c1547.customerId]!!.cuff, 0.01)

    // no.1644: "کف 5۔20" -> bazu 20.5, cuff 0.0 (NOT 20.5)
    val c1644 = custByCustomId["no.1644"]!!
    assertEquals(20.5, measByCustomer[c1644.customerId]!!.bazu, 0.01)
    assertEquals(0.0, measByCustomer[c1644.customerId]!!.cuff, 0.01)
    assertEquals(SleeveStyle.KAF, measByCustomer[c1644.customerId]!!.sleeveStyle)

    // no.1609: "4ٍ1" diacritic stripped -> 41.0
    val c1609 = custByCustomId["no.1609"]!!
    assertEquals(41.0, measByCustomer[c1609.customerId]!!.shalwarLambai, 0.01)

    // no.1852: "24 کف 9 گول" -> complete valid measurement
    val c1852 = custByCustomId["no.1852"]!!
    val m1852 = measByCustomer[c1852.customerId]!!
    assertEquals(24.0, m1852.bazu, 0.01)
    assertEquals(9.0, m1852.cuff, 0.01)

    // no.1901: "5۔23 مکف 9" -> bazu 23.5, cuff 9.0
    val c1901 = custByCustomId["no.1901"]!!
    assertEquals(23.5, measByCustomer[c1901.customerId]!!.bazu, 0.01)
    assertEquals(9.0, measByCustomer[c1901.customerId]!!.cuff, 0.01)

    // no.2080: "5۔21 ک5۔8" -> bazu 21.5, cuff 8.5
    val c2080 = custByCustomId["no.2080"]!!
    assertEquals(21.5, measByCustomer[c2080.customerId]!!.bazu, 0.01)
    assertEquals(8.5, measByCustomer[c2080.customerId]!!.cuff, 0.01)

    // no.1869: "22 9سوا کف" -> bazu 22.0, cuff 9.25
    val c1869 = custByCustomId["no.1869"]!!
    assertEquals(22.0, measByCustomer[c1869.customerId]!!.bazu, 0.01)
    assertEquals(9.25, measByCustomer[c1869.customerId]!!.cuff, 0.01)

    // no.2045: both FRONT and ONE_SIDE pockets
    val c2045 = custByCustomId["no.2045"]!!
    val m2045 = measByCustomer[c2045.customerId]!!
    assertTrue(PocketStyle.FRONT in m2045.pocketStyles)
    assertTrue(PocketStyle.ONE_SIDE in m2045.pocketStyles)

    // Shifted/incomplete rows saved without measurements with labeled notes & phones preserved
    val c1758 = custByCustomId["no.1758"]!!
    assertEquals(null, measByCustomer[c1758.customerId])
    assertEquals("0312-7545112", c1758.phone)
    assertTrue(c1758.notes.contains("تیرا درج نہیں"))

    val c1976 = custByCustomId["no.1976"]!!
    assertEquals(null, measByCustomer[c1976.customerId])
    assertTrue(c1976.notes.contains("گھیرا درج نہیں"))
  }

  @Test
  fun urduCsvParserHandlesSheet4AnomaliesAndShiftedRows() {
    val json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
    }
    val csv = """
      کسٹمر آئی ڈی,نام ,قمیض لمبائی,تیرا,آستین,گلا ,سینہ,گھیرا/دامن,شلوار,پانچہ,,فون نمبر
      no.2126,مدثر ولد اسمعیل خان وپاری,May-38,17,5۔21 ک5۔8,15 .5کالر,آرمول 8 34 تیار سینہ,24,22.5,4ٍ1,3071912053کمانڈو/03087516853,
      no.1758,عثمان غنی ولد حاجی نیاز,5۔36,22 کف پونے 9,5۔13 بین,33 سینہ,5۔18,37جیب,5۔6,3127545112کماونڈ ڈبل سلائی,,
    """.trimIndent()

    val encoded = UrduSheetCsvParser.convertCsvToBackupJson(csv, json, nowMillis = 1763950000000L).getOrThrow()
    val bundle = json.decodeFromString<DarziBackupBundle>(encoded)
    assertEquals(2, bundle.customers.size)
    assertEquals(1, bundle.measurements.size)

    val c1 = bundle.customers[0]
    assertEquals("0307-1912053", c1.phone)
    assertTrue(c1.notes.contains("0308-7516853"))
    val m1 = bundle.measurements.single()
    assertEquals(38.5, m1.kameezLambai, 0.01)
    assertEquals(21.5, m1.bazu, 0.01)
    assertEquals(8.5, m1.cuff, 0.01)
    assertEquals(15.5, m1.galaCollar, 0.01)
    assertEquals(NeckStyle.COLLAR, m1.neckStyle)
    assertEquals(34.0, m1.chati, 0.01)

    val c2 = bundle.customers[1]
    assertEquals("0312-7545112", c2.phone)
    assertTrue(c2.notes.contains("تیرا درج نہیں"))
  }

  @Test
  fun sheet5BackupAssetValidatesAgainstFirestoreAndTailorSchema() {
    val json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
    }

    val assetFile = java.io.File("src/main/assets/google_sheet_700_backup.json")
      .takeIf { it.exists() }
      ?: java.io.File("app/src/main/assets/google_sheet_700_backup.json")
    assertTrue("Expected bundled Sheet 5 asset file to exist", assetFile.exists())

    val bundle = json.decodeFromString<DarziBackupBundle>(assetFile.readText(Charsets.UTF_8))
    assertEquals(ShopBackupRepository.APP_IDENTIFIER, bundle.appId)
    assertEquals(628, bundle.customers.size)
    assertEquals(628, bundle.measurements.size)

    val customerIds = bundle.customers.map { it.customerId }.toSet()
    assertEquals(628, customerIds.size)
    assertEquals(628, bundle.measurements.map { it.measurementId }.toSet().size)

    val idPattern = Regex("""^[a-zA-Z0-9_-]+$""")
    bundle.customers.forEach { c ->
      assertTrue(idPattern.matches(c.customerId))
      assertTrue(c.name.isNotBlank() && c.name.length <= 100)
      assertTrue(c.customId.length <= 40)
      assertTrue(c.phone.length <= 30)
      assertTrue(c.normalizedPhone.length <= 30)
      assertTrue(c.notes.length <= 500)
    }

    bundle.measurements.forEach { m ->
      assertTrue(idPattern.matches(m.measurementId))
      assertTrue(m.customerId in customerIds)
      assertTrue("lambai > 0 for ${m.measurementId}", m.kameezLambai > 0.0 && m.kameezLambai <= 200.0)
      assertTrue("tera > 0 for ${m.measurementId}", m.tera > 0.0 && m.tera <= 200.0)
      assertTrue("bazu > 0 for ${m.measurementId}", m.bazu > 0.0 && m.bazu <= 200.0)
      assertTrue("chati > 0 for ${m.measurementId}", m.chati > 0.0 && m.chati <= 200.0)
      assertTrue("gheraDaman > 0 for ${m.measurementId}", m.gheraDaman > 0.0 && m.gheraDaman <= 200.0)
      assertTrue(m.galaCollar in 0.0..200.0)
      assertTrue(m.cuff in 0.0..200.0)
      assertTrue(m.shalwarLambai in 0.0..200.0)
      assertTrue(m.paincha in 0.0..200.0)
      assertTrue(m.shalwarGhair in 0.0..200.0)
      assertTrue(m.notes.length <= 500)
      assertTrue(m.rawInputsJson.length <= 500)
    }

    val custByCustomId = bundle.customers.associateBy { it.customId }
    val measByCustomer = bundle.measurements.associateBy { it.customerId }

    // no.2307: "(5۔11 انیچ تیار)38" -> lambai 38.0 (not 11.5)
    val c2307 = custByCustomId["no.2307"]!!
    assertEquals(38.0, measByCustomer[c2307.customerId]!!.kameezLambai, 0.01)

    // no.2295: "(1.1/2) کالر چھوٹی نوک14" -> gala 14.0 COLLAR
    val c2295 = custByCustomId["no.2295"]!!
    assertEquals(14.0, measByCustomer[c2295.customerId]!!.galaCollar, 0.01)
    assertEquals(NeckStyle.COLLAR, measByCustomer[c2295.customerId]!!.neckStyle)

    // no.2252: "9 کرتا 5۔3" in tera is NOT shifted
    val c2252 = custByCustomId["no.2252"]!!
    val m2252 = measByCustomer[c2252.customerId]!!
    assertEquals(9.0, m2252.tera, 0.01)
    assertEquals(8.5, m2252.bazu, 0.01)
    assertEquals(SleeveStyle.KURTA, m2252.sleeveStyle)

    // no.2486: missing gala shifted row remapped cleanly
    val c2486 = custByCustomId["no.2486"]!!
    val m2486 = measByCustomer[c2486.customerId]!!
    assertEquals(0.0, m2486.galaCollar, 0.01)
    assertEquals(31.0, m2486.chati, 0.01)
    assertEquals(18.0, m2486.gheraDaman, 0.01)
    assertEquals(33.5, m2486.shalwarLambai, 0.01)
    assertEquals(6.5, m2486.paincha, 0.01)

    // no.2747: missing gala shifted row + 16-Jan shalwar
    val c2747 = custByCustomId["no.2747"]!!
    val m2747 = measByCustomer[c2747.customerId]!!
    assertEquals(22.0, m2747.chati, 0.01)
    assertEquals(14.0, m2747.gheraDaman, 0.01)
    assertEquals(16.0, m2747.shalwarLambai, 0.01)
    assertEquals(4.5, m2747.paincha, 0.01)

    // no.2725..no.2750 dd-Jan dates in ghera
    assertEquals(11.0, measByCustomer[custByCustomId["no.2725"]!!.customerId]!!.gheraDaman, 0.01)
    assertEquals(28.0, measByCustomer[custByCustomId["no.2746"]!!.customerId]!!.gheraDaman, 0.01)

    // no.2496: "11,5بیبن" -> 11.5 BAN
    val c2496 = custByCustomId["no.2496"]!!
    assertEquals(11.5, measByCustomer[c2496.customerId]!!.galaCollar, 0.01)
    assertEquals(NeckStyle.BAN, measByCustomer[c2496.customerId]!!.neckStyle)

    // no.2596: "10.5 ین" -> 10.5 BAN
    val c2596 = custByCustomId["no.2596"]!!
    assertEquals(10.5, measByCustomer[c2596.customerId]!!.galaCollar, 0.01)
    assertEquals(NeckStyle.BAN, measByCustomer[c2596.customerId]!!.neckStyle)

    // no.2444: "22کف9 ہونے" -> 8.75 cuff
    assertEquals(8.75, measByCustomer[custByCustomId["no.2444"]!!.customerId]!!.cuff, 0.01)

    // no.2669: "22.5سو9" -> 9.25 cuff
    assertEquals(9.25, measByCustomer[custByCustomId["no.2669"]!!.customerId]!!.cuff, 0.01)

    // no.2399: "5۔ٓ6" diacritic stripped -> 6.5 paincha
    assertEquals(6.5, measByCustomer[custByCustomId["no.2399"]!!.customerId]!!.paincha, 0.01)

    // no.2540: "5۔ٍ12" diacritic stripped -> 12.5 daman + ONE_SIDE pocket in asteen
    val m2540 = measByCustomer[custByCustomId["no.2540"]!!.customerId]!!
    assertEquals(12.5, m2540.gheraDaman, 0.01)
    assertTrue(PocketStyle.ONE_SIDE in m2540.pocketStyles)

    // no.2359: "40گھیر 38" -> shalwarGhair 40.0, shalwarLambai 38.0
    val m2359 = measByCustomer[custByCustomId["no.2359"]!!.customerId]!!
    assertEquals(40.0, m2359.shalwarGhair, 0.01)
    assertEquals(38.0, m2359.shalwarLambai, 0.01)

    // no.2180: dual phone numbers
    val c2180 = custByCustomId["no.2180"]!!
    assertEquals("0310-4306342", c2180.phone)
    assertTrue(c2180.notes.contains("0309-5281909"))

    // no.2657: incomplete 10-digit phone in customer notes
    val c2657 = custByCustomId["no.2657"]!!
    assertEquals("", c2657.phone)
    assertTrue(c2657.notes.contains("0387395609"))
  }

  @Test
  fun sheet6BackupAssetValidatesAgainstFirestoreAndTailorSchema() {
    val json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
    }

    val assetFile = java.io.File("src/main/assets/google_sheet_1000_backup.json")
      .takeIf { it.exists() }
      ?: java.io.File("app/src/main/assets/google_sheet_1000_backup.json")
    assertTrue("Expected bundled Sheet 6 asset file to exist", assetFile.exists())

    val bundle = json.decodeFromString<DarziBackupBundle>(assetFile.readText(Charsets.UTF_8))
    assertEquals(ShopBackupRepository.APP_IDENTIFIER, bundle.appId)
    assertEquals(503, bundle.customers.size)
    assertEquals(502, bundle.measurements.size)

    val customerIds = bundle.customers.map { it.customerId }.toSet()
    assertEquals(503, customerIds.size)
    assertEquals(502, bundle.measurements.map { it.measurementId }.toSet().size)

    val idPattern = Regex("""^[a-zA-Z0-9_-]+$""")
    bundle.customers.forEach { c ->
      assertTrue(idPattern.matches(c.customerId))
      assertTrue(c.name.isNotBlank() && c.name.length <= 100)
      assertTrue(c.customId.length <= 40)
      assertTrue(c.phone.length <= 30)
      assertTrue(c.normalizedPhone.length <= 30)
      assertTrue(c.notes.length <= 500)
    }

    bundle.measurements.forEach { m ->
      assertTrue(idPattern.matches(m.measurementId))
      assertTrue(m.customerId in customerIds)
      assertTrue("lambai > 0 for ${m.measurementId}", m.kameezLambai > 0.0 && m.kameezLambai <= 200.0)
      assertTrue("tera > 0 for ${m.measurementId}", m.tera > 0.0 && m.tera <= 200.0)
      assertTrue("bazu > 0 for ${m.measurementId}", m.bazu > 0.0 && m.bazu <= 200.0)
      assertTrue("chati > 0 for ${m.measurementId}", m.chati > 0.0 && m.chati <= 200.0)
      assertTrue("gheraDaman > 0 for ${m.measurementId}", m.gheraDaman > 0.0 && m.gheraDaman <= 200.0)
      assertTrue(m.galaCollar in 0.0..200.0)
      assertTrue(m.cuff in 0.0..200.0)
      assertTrue(m.shalwarLambai in 0.0..200.0)
      assertTrue(m.paincha in 0.0..200.0)
      assertTrue(m.shalwarGhair in 0.0..200.0)
      assertTrue(m.notes.length <= 500)
      assertTrue(m.rawInputsJson.length <= 500)
    }

    val custByCustomId = bundle.customers.associateBy { it.customId }
    val measByCustomer = bundle.measurements.associateBy { it.customerId }

    // no.3084: empty Tera shifted right remapped cleanly
    val c3084 = custByCustomId["no.3084"]!!
    val m3084 = measByCustomer[c3084.customerId]!!
    assertEquals(15.5, m3084.kameezLambai, 0.01)
    assertEquals(8.5, m3084.tera, 0.01)
    assertEquals(8.5, m3084.bazu, 0.01)
    assertEquals(SleeveStyle.KURTA, m3084.sleeveStyle)
    assertEquals(10.0, m3084.galaCollar, 0.01)
    assertEquals(NeckStyle.BAN, m3084.neckStyle)
    assertEquals(22.0, m3084.chati, 0.01)
    assertEquals(13.0, m3084.gheraDaman, 0.01)
    assertEquals(16.0, m3084.shalwarLambai, 0.01)
    assertEquals(4.5, m3084.paincha, 0.01)

    // no.3203: missing Chati 'ی' -> omitted measurement + detailed incomplete note
    val c3203 = custByCustomId["no.3203"]!!
    assertEquals(null, measByCustomer[c3203.customerId])
    assertTrue(c3203.notes.contains("ناپ نامکمل"))
    assertTrue(c3203.notes.contains("سیٹ جیب لگانی ہے"))

    // no.2873: "16-Jan" asteen -> 16.0 and "16-May" daman -> 16.5
    val m2873 = measByCustomer[custByCustomId["no.2873"]!!.customerId]!!
    assertEquals(16.0, m2873.bazu, 0.01)
    assertEquals(16.5, m2873.gheraDaman, 0.01)

    // no.2756: "شلوار گھیر 23" in paincha col -> shalwarGhair 23.0, paincha 0.0
    val m2756 = measByCustomer[custByCustomId["no.2756"]!!.customerId]!!
    assertEquals(23.0, m2756.shalwarGhair, 0.01)
    assertEquals(0.0, m2756.paincha, 0.01)

    // no.2779 & no.2865 & no.2866: comma-5 and double-dot decimals
    val m2779 = measByCustomer[custByCustomId["no.2779"]!!.customerId]!!
    assertEquals(14.5, m2779.bazu, 0.01)
    assertEquals(7.5, m2779.cuff, 0.01)
    assertEquals(11.5, m2779.galaCollar, 0.01)
    assertEquals(6.5, measByCustomer[custByCustomId["no.2865"]!!.customerId]!!.paincha, 0.01)
    assertEquals(17.5, measByCustomer[custByCustomId["no.2866"]!!.customerId]!!.gheraDaman, 0.01)

    // no.2916: "13سواکف7" -> bazu 13.0, cuff 7.25
    val m2916 = measByCustomer[custByCustomId["no.2916"]!!.customerId]!!
    assertEquals(13.0, m2916.bazu, 0.01)
    assertEquals(7.25, m2916.cuff, 0.01)

    // no.3008: "12 یین" -> 12.0 BAN; no.2801: "5۔11 بین پنوی" -> 11.5 HALF_BAN
    assertEquals(NeckStyle.BAN, measByCustomer[custByCustomId["no.3008"]!!.customerId]!!.neckStyle)
    assertEquals(NeckStyle.HALF_BAN, measByCustomer[custByCustomId["no.2801"]!!.customerId]!!.neckStyle)

    // no.3247: "13.5جیب سیٹ دونوں" -> TWO_SIDE pocket
    val m3247 = measByCustomer[custByCustomId["no.3247"]!!.customerId]!!
    assertTrue(PocketStyle.TWO_SIDE in m3247.pocketStyles)

    // no.3202: "سیٹ جیب لگانی ہے" in extra -> ONE_SIDE pocket
    val m3202 = measByCustomer[custByCustomId["no.3202"]!!.customerId]!!
    assertTrue(PocketStyle.ONE_SIDE in m3202.pocketStyles)

    // no.2805 & no.3135: phone extraction with trailing name
    val c2805 = custByCustomId["no.2805"]!!
    assertEquals("0311-6262572", c2805.phone)
    assertTrue(measByCustomer[c2805.customerId]!!.notes.contains("آصف لائل"))

    val c3135 = custByCustomId["no.3135"]!!
    assertEquals("0300-2208173", c3135.phone)
  }

  @Test
  fun urduCsvParserHandlesSheet6AnomaliesAndShiftedRows() {
    val json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
    }
    val csv = """
      کسٹمر آئی ڈی,نام ,قمیض لمبائی,تیرا,آستین,گلا ,سینہ,گھیرا/دامن,شلوار,پانچہ,,فون نمبر
      no.3084,ظوحان ولد فرحان جھنڈیر,15.5,,8.5,8.5کرتا,10بین,22,13,16,4.5,
      no.2873,سبان ولد ولد شاکر لائل,27,5۔12,16-Jan,5۔11 بین,26سینہ,16-May,27,6,,
      no.3203,محبوب ولد عمران بلوچ,5۔20,11,5۔12کرتا,11بین,ی,5۔13,19,5,سیٹ جیب لگانی ہے,
    """.trimIndent()

    val encoded = UrduSheetCsvParser.convertCsvToBackupJson(csv, json, nowMillis = 1764000000000L).getOrThrow()
    val bundle = json.decodeFromString<DarziBackupBundle>(encoded)
    assertEquals(3, bundle.customers.size)
    assertEquals(2, bundle.measurements.size)

    val m0 = bundle.measurements[0]
    assertEquals(8.5, m0.tera, 0.01)
    assertEquals(8.5, m0.bazu, 0.01)
    assertEquals(10.0, m0.galaCollar, 0.01)
    assertEquals(22.0, m0.chati, 0.01)
    assertEquals(13.0, m0.gheraDaman, 0.01)
    assertEquals(16.0, m0.shalwarLambai, 0.01)
    assertEquals(4.5, m0.paincha, 0.01)

    val m1 = bundle.measurements[1]
    assertEquals(16.0, m1.bazu, 0.01)
    assertEquals(16.5, m1.gheraDaman, 0.01)

    val c2 = bundle.customers[2]
    assertTrue(c2.notes.contains("ناپ نامکمل"))
    assertTrue(c2.notes.contains("گھیرا 13.5"))
    assertTrue(c2.notes.contains("سیٹ جیب لگانی ہے"))
  }
}
