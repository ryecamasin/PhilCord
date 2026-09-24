package ph.philcord.mobile;

import java.util.Arrays;

final class DnsPacket {
    private static final int IPV4_MIN_HEADER = 20;
    private static final int UDP_HEADER = 8;

    private final byte[] request;
    private final int ipHeaderLength;
    private final int udpOffset;
    private final int dnsOffset;
    private final int dnsLength;

    private DnsPacket(byte[] request, int ipHeaderLength, int udpOffset, int dnsOffset, int dnsLength) {
        this.request = request;
        this.ipHeaderLength = ipHeaderLength;
        this.udpOffset = udpOffset;
        this.dnsOffset = dnsOffset;
        this.dnsLength = dnsLength;
    }

    static DnsPacket parse(byte[] packet, int length) {
        if (length < IPV4_MIN_HEADER + UDP_HEADER + 12) return null;
        if ((packet[0] >> 4 & 0x0f) != 4) return null;
        int ipHeaderLength = (packet[0] & 0x0f) * 4;
        if (ipHeaderLength < IPV4_MIN_HEADER || length < ipHeaderLength + UDP_HEADER + 12) return null;
        if ((packet[9] & 0xff) != 17) return null;

        int udpOffset = ipHeaderLength;
        int destinationPort = unsignedShort(packet, udpOffset + 2);
        if (destinationPort != 53) return null;

        int udpLength = unsignedShort(packet, udpOffset + 4);
        int dnsOffset = udpOffset + UDP_HEADER;
        int dnsLength = Math.min(udpLength - UDP_HEADER, length - dnsOffset);
        if (dnsLength < 12) return null;

        return new DnsPacket(Arrays.copyOf(packet, length), ipHeaderLength, udpOffset, dnsOffset, dnsLength);
    }

    byte[] dnsMessage() {
        return Arrays.copyOfRange(request, dnsOffset, dnsOffset + dnsLength);
    }

    byte[] buildResponse(byte[] dnsResponse) {
        int udpLength = UDP_HEADER + dnsResponse.length;
        int totalLength = IPV4_MIN_HEADER + udpLength;
        byte[] response = new byte[totalLength];

        response[0] = 0x45;
        response[1] = 0;
        putShort(response, 2, totalLength);
        response[4] = request[4];
        response[5] = request[5];
        response[6] = 0;
        response[7] = 0;
        response[8] = 64;
        response[9] = 17;

        System.arraycopy(request, 16, response, 12, 4);
        System.arraycopy(request, 12, response, 16, 4);

        int responseUdpOffset = IPV4_MIN_HEADER;
        putShort(response, responseUdpOffset, 53);
        putShort(response, responseUdpOffset + 2, unsignedShort(request, udpOffset));
        putShort(response, responseUdpOffset + 4, udpLength);
        putShort(response, responseUdpOffset + 6, 0);
        System.arraycopy(dnsResponse, 0, response, responseUdpOffset + UDP_HEADER, dnsResponse.length);

        putShort(response, 10, checksum(response, 0, IPV4_MIN_HEADER));
        return response;
    }

    byte[] buildServerFailure() {
        byte[] failure = dnsMessage();
        failure[2] = (byte) ((failure[2] & 0x79) | 0x80);
        failure[3] = (byte) ((failure[3] & 0xf0) | 0x02);
        failure[6] = failure[7] = 0;
        failure[8] = failure[9] = 0;
        failure[10] = failure[11] = 0;
        return buildResponse(failure);
    }

    private static int unsignedShort(byte[] data, int offset) {
        return (data[offset] & 0xff) << 8 | data[offset + 1] & 0xff;
    }

    private static void putShort(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
    }

    private static int checksum(byte[] data, int offset, int length) {
        long sum = 0;
        int end = offset + length;
        for (int i = offset; i + 1 < end; i += 2) {
            sum += unsignedShort(data, i);
            sum = (sum & 0xffff) + (sum >>> 16);
        }
        if ((length & 1) != 0) sum += (data[end - 1] & 0xff) << 8;
        while ((sum >>> 16) != 0) sum = (sum & 0xffff) + (sum >>> 16);
        return (int) ~sum & 0xffff;
    }
}
