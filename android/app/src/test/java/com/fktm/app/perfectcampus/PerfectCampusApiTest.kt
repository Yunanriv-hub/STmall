package com.fktm.app.perfectcampus

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PerfectCampusApiTest {

    @Test
    fun parsesBoundRoomsAndFiltersMissingVerify() {
        val rooms = parsePerfectCampusRooms(
            JSONObject(
                """
                {
                  "result": "0",
                  "roomlist": [
                    {
                      "roomfullname": "南区 2#公寓 24603",
                      "roomverify": "99-5--20-24603",
                      "detaillist": [
                        {"use": "100.2", "odd": "19.8", "businesstype": "0", "status": "1"}
                      ]
                    },
                    {"roomfullname": "无效房间", "detaillist": []}
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(1, rooms.size)
        assertEquals("99-5--20-24603", rooms.single().verify)
        assertEquals("19.8", rooms.single().meters.single().remaining)
    }

    @Test
    fun parsesRoomDetailAndWeeklyUsage() {
        val detail = parsePerfectCampusRoomDetail(
            JSONObject(
                """
                {
                  "result": "0",
                  "roomfullname": "西区 12 栋 12619",
                  "roomnum": "12619",
                  "modlist": [
                    {
                      "devicename": "电控",
                      "odd": "232.7",
                      "sumbuy": "484.6",
                      "sumsub": "17994.57",
                      "todayuse": "14.8",
                      "weekuselist": [
                        {"weekday": "星期三", "date": "09-04", "dayuse": "38"}
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals("西区 12 栋 12619", detail.roomName)
        assertEquals("14.8", detail.devices.single().todayUsed)
        assertEquals("星期三 09-04", detail.devices.single().weekUsage.single().date)
    }
}
