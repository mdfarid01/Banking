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

        // ===== BUILD RESPONSE HEADER =====
        byte[] response = new byte[12];

        // ID
        response[0] = (byte) (requestId >> 8);
        response[1] = (byte) requestId;

        int responseFlags = 0;
        responseFlags |= (1 << 15);        // QR
        responseFlags |= (opcode << 11);   // OPCODE
        responseFlags |= (rd << 8);        // RD

        if (opcode != 0) {
          responseFlags |= 4;              // RCODE = Not Implemented
        }

        response[2] = (byte) (responseFlags >> 8);
        response[3] = (byte) responseFlags;

        // QDCOUNT (any valid value)
        response[4] = 0x00;
        response[5] = 0x01;

        // ANCOUNT
        response[6] = 0x00;
        response[7] = 0x00;

        // NSCOUNT
        response[8] = 0x00;
        response[9] = 0x00;

        // ARCOUNT
        response[10] = 0x00;
        response[11] = 0x00;

        DatagramPacket responsePacket =
            new DatagramPacket(response, response.length, packet.getSocketAddress());

        serverSocket.send(responsePacket);
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }
}
