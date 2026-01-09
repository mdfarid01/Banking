import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Main {

  public static void main(String[] args) {
    System.out.println("Logs from your program will appear here!");

    try (DatagramSocket serverSocket = new DatagramSocket(2053)) {
      while (true) {

        byte[] request = new byte[512];
        DatagramPacket packet = new DatagramPacket(request, request.length);
        serverSocket.receive(packet);

        // ===== PARSE HEADER =====
        int requestId =
            ((request[0] & 0xFF) << 8) | (request[1] & 0xFF);

        int requestFlags =
            ((request[2] & 0xFF) << 8) | (request[3] & 0xFF);

        int opcode = (requestFlags >> 11) & 0xF;
        int rd = (requestFlags >> 8) & 0x1;

        // ===== PARSE QUESTION =====
        int offset = 12;

        // Save raw QNAME bytes
        int qnameStart = offset;
        while (request[offset] != 0x00) {
          offset += (request[offset] & 0xFF) + 1;
        }
        offset++; // include null byte

        int qnameLength = offset - qnameStart;
        byte[] qname = new byte[qnameLength];
        System.arraycopy(request, qnameStart, qname, 0, qnameLength);

        // QTYPE (2 bytes)
        int qtype = ((request[offset] & 0xFF) << 8)
                  | (request[offset + 1] & 0xFF);
        offset += 2;

        // QCLASS (2 bytes)
        int qclass = ((request[offset] & 0xFF) << 8)
                   | (request[offset + 1] & 0xFF);
        offset += 2;

        // ===== BUILD RESPONSE =====
        byte[] response = new byte[512];
        int rOffset = 0;

        // ID
        response[rOffset++] = (byte) (requestId >> 8);
        response[rOffset++] = (byte) requestId;

        // FLAGS
        int responseFlags = 0;
        responseFlags |= (1 << 15);       // QR
        responseFlags |= (opcode << 11);  // OPCODE
        responseFlags |= (rd << 8);       // RD

        if (opcode != 0) {
          responseFlags |= 4;             // RCODE = Not Implemented
        }

        response[rOffset++] = (byte) (responseFlags >> 8);
        response[rOffset++] = (byte) responseFlags;

        // QDCOUNT = 1
        response[rOffset++] = 0x00;
        response[rOffset++] = 0x01;

        // ANCOUNT = 1
        response[rOffset++] = 0x00;
        response[rOffset++] = 0x01;

        // NSCOUNT = 0
        response[rOffset++] = 0x00;
        response[rOffset++] = 0x00;

        // ARCOUNT = 0
        response[rOffset++] = 0x00;
        response[rOffset++] = 0x00;

        // ===== QUESTION SECTION =====
        System.arraycopy(qname, 0, response, rOffset, qname.length);
        rOffset += qname.length;

        response[rOffset++] = (byte) (qtype >> 8);
        response[rOffset++] = (byte) qtype;

        response[rOffset++] = (byte) (qclass >> 8);
        response[rOffset++] = (byte) qclass;

        // ===== ANSWER SECTION =====
        System.arraycopy(qname, 0, response, rOffset, qname.length);
        rOffset += qname.length;

        // TYPE A
        response[rOffset++] = 0x00;
        response[rOffset++] = 0x01;

        // CLASS IN
        response[rOffset++] = 0x00;
        response[rOffset++] = 0x01;

        // TTL = 60
        response[rOffset++] = 0x00;
        response[rOffset++] = 0x00;
        response[rOffset++] = 0x00;
        response[rOffset++] = 0x3c;

        // RDLENGTH = 4
        response[rOffset++] = 0x00;
        response[rOffset++] = 0x04;

        // RDATA = 8.8.8.8
        response[rOffset++] = 0x08;
        response[rOffset++] = 0x08;
        response[rOffset++] = 0x08;
        response[rOffset++] = 0x08;

        DatagramPacket responsePacket =
            new DatagramPacket(response, rOffset, packet.getSocketAddress());

        serverSocket.send(responsePacket);
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }
}
