package com.generic.tools.ai.tools;


import dev.langchain4j.agent.tool.Tool;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class NtpTimeTool {

    // Public NTP server pool
    private static final String NTP_SERVER = "pool.ntp.org";

    @Tool("""
        Returns the precise current date and time using the Network Time Protocol (NTP).
        Usage:
        - Use this when the user asks for the current time or date.
        - If a specific timezone is requested (e.g., 'Asia/Kolkata'), pass it as the argument.
        - If no timezone is specified, pass 'default'.
        """)
    public String getNtpTime(String timeZoneQuery) {
        try {
            // 1. Resolve TimeZone
            String timeZoneId = resolveTimeZone(timeZoneQuery);
            
            // 2. Query NTP Server
            System.out.println("DEBUG: Querying NTP server: " + NTP_SERVER);
            long ntpTime = queryNtpServer(NTP_SERVER);
            
            // 3. Format Date
            SimpleDateFormat formatter = new SimpleDateFormat("EEEE, dd MMMM yyyy, HH:mm:ss z");
            formatter.setTimeZone(TimeZone.getTimeZone(timeZoneId));
            
            return "Source: NTP (pool.ntp.org)\n" +
                   "Time: " + formatter.format(new Date(ntpTime));

        } catch (Exception e) {
            e.printStackTrace();
            return "Error: Could not fetch time via NTP. Network blocked on UDP Port 123. Details: " + e.getMessage();
        }
    }

    // --- LOW LEVEL NTP IMPLEMENTATION (RFC 2030) ---
    private long queryNtpServer(String serverAddress) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(5000); // 5 second timeout

        InetAddress address = InetAddress.getByName(serverAddress);

        // NTP message size is 48 bytes
        byte[] buf = new byte[48];
        // 00100011 (LI = 0, VN = 4, Mode = 3 for Client)
        buf[0] = 0x1B;

        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, 123);

        // 1. Send Request
        socket.send(packet);

        // 2. Receive Response
        packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);

        // 3. Parse Timestamp (Transmit Timestamp starts at byte 40)
        // NTP time is seconds since 1900. Java time is milliseconds since 1970.
        long unsignedSeconds = 0;
        for (int i = 40; i <= 43; i++) {
            unsignedSeconds = (unsignedSeconds << 8) | (buf[i] & 0xff);
        }
        
        // Convert NTP seconds to Java milliseconds
        // 2208988800L is the offset in seconds between 1900 and 1970
        long time = (unsignedSeconds - 2208988800L) * 1000;
        
        socket.close();
        return time;
    }

    private String resolveTimeZone(String query) {
        if (query == null || query.isBlank() || query.equalsIgnoreCase("default")) {
            return "Asia/Kolkata";
        }
        // Basic mapping or use simple partial match
        String q = query.toLowerCase().replace(" ", "_");
        for (String id : TimeZone.getAvailableIDs()) {
            if (id.toLowerCase().contains(q)) return id;
        }
        return "Asia/Kolkata";
    }
}