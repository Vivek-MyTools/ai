package com.generic.tools.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class TimeTool {

    private static final String NTP_SERVER = "time.google.com";

    @Tool("""
            Provides the current real-world date and time using an internet time server (NTP).

            STRICT USAGE RULES:
            1. Use this tool ONLY when the user asks for the current time, date, or "today's time".
            2. DO NOT use this tool for historical dates, time calculations, or time conversions.
            3. DO NOT guess or fabricate time values.
            4. Output reflects authoritative internet time, not the system clock.
            """)
    public String getCurrentTime() {
        System.out.println("   --> ⏰ Time Tool: Fetching current time from NTP server");
        try {
            NTPUDPClient client = new NTPUDPClient();
            client.setDefaultTimeout(3000);

            InetAddress hostAddr = InetAddress.getByName(NTP_SERVER);
            TimeInfo timeInfo = client.getTime(hostAddr);

            long ntpTime = timeInfo.getMessage()
                                   .getTransmitTimeStamp()
                                   .getTime();

            ZonedDateTime dateTime = Instant.ofEpochMilli(ntpTime)
                                            .atZone(ZoneId.systemDefault());

            return dateTime.toString();

        } catch (Exception e) {
            System.err.println("TimeTool error: " + e.getMessage());
            return "ERROR: Unable to fetch current time";
        }
    }
}
