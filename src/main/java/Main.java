import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Main {

  public static void main(String[] args) {
    System.out.println("Logs from your program will appear here!");

    try (DatagramSocket serverSocket = new DatagramSocket(2053)) {
      while (true) {

        byte[] buf = new byte[512];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        serverSocket.receive(packet);

        // ===== PARSE REQUEST HEADER =====
        int requestId =
            ((buf[0] & 0xFF) << 8) | (buf[1] & 0xFF);

        int requestFlags =
            ((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);

        int opcode = (requestFlags >> 11) & 0xF;
        int rd = (requestFlags >> 8) & 0x1;

        byte[] response = new byte[512];
        int offset = 0;

        // ===== HEADER =====
        response[offset++] = (byte) (requestId >> 8);
        response[offset++] = (byte) requestId;

        int responseFlags = 0;
        responseFlags |= (1 << 15);       // QR
        responseFlags |= (opcode << 11);  // OPCODE
        responseFlags |= (rd << 8);       // RD

        if (opcode != 0) {
          responseFlags |= 4;             // RCODE = Not Implemented
        }

        response[offset++] = (byte) (responseFlags >> 8);
        response[offset++] = (byte) responseFlags;

        // QDCOUNT = 1
        response[offset++] = 0x00;
        response[offset++] = 0x01;

        // ANCOUNT = 1
        response[offset++] = 0x00;
        response[offset++] = 0x01;

        // NSCOUNT = 0
        response[offset++] = 0x00;
        response[offset++] = 0x00;

        // ARCOUNT = 0
        response[offset++] = 0x00;
        response[offset++] = 0x00;

        // ===== QUESTION SECTION =====
        response[offset++] = 0x0c;
        byte[] label1 = "codecrafters".getBytes();
        System.arraycopy(label1, 0, response, offset, label1.length);
        offset += label1.length;

        response[offset++] = 0x02;
        byte[] label2 = "io".getBytes();
        System.arraycopy(label2, 0, response, offset, label2.length);
        offset += label2.length;

        response[offset++] = 0x00;

        response[offset++] = 0x00; // QTYPE A
        response[offset++] = 0x01;

        response[offset++] = 0x00; // QCLASS IN
        response[offset++] = 0x01;

        // ===== ANSWER SECTION =====
        response[offset++] = 0x0c;
        System.arraycopy(label1, 0, response, offset, label1.length);
        offset += label1.length;

        response[offset++] = 0x02;
        System.arraycopy(label2, 0, response, offset, label2.length);
        offset += label2.length;

        response[offset++] = 0x00;

        response[offset++] = 0x00; // TYPE A
        response[offset++] = 0x01;

        response[offset++] = 0x00; // CLASS IN
        response[offset++] = 0x01;

        // TTL = 60
        response[offset++] = 0x00;
        response[offset++] = 0x00;
        response[offset++] = 0x00;
        response[offset++] = 0x3c;

        // RDLENGTH = 4
        response[offset++] = 0x00;
        response[offset++] = 0x04;

        // RDATA = 8.8.8.8
        response[offset++] = 0x08;
        response[offset++] = 0x08;
        response[offset++] = 0x08;
        response[offset++] = 0x08;

        DatagramPacket responsePacket =
            new DatagramPacket(response, offset, packet.getSocketAddress());

        serverSocket.send(responsePacket);
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }
}
