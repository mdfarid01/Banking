import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Main {

  private static void setPacketId(byte[] response, int id) {
    response[0] = (byte) ((id >> 8) & 0xFF);
    response[1] = (byte) (id & 0xFF);
  }

  private static void setQR(byte[] response) {
    response[2] |= (byte) (1 << 7); // QR = 1 (response)
  }

  public static void main(String[] args) {
    System.out.println("Logs from your program will appear here!");

    try (DatagramSocket serverSocket = new DatagramSocket(2053)) {
      while (true) {
        byte[] buf = new byte[512];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        serverSocket.receive(packet);

        byte[] bufResponse = new byte[512];
        int offset = 0;

        // ===== HEADER =====
        setPacketId(bufResponse, 1234);
        offset += 2;

        setQR(bufResponse);
        offset += 2;

        // QDCOUNT = 1
        bufResponse[offset++] = 0x00;
        bufResponse[offset++] = 0x01;

        // ANCOUNT = 1  ✅ NEW
        bufResponse[offset++] = 0x00;
        bufResponse[offset++] = 0x01;

        // NSCOUNT = 0
        bufResponse[offset++] = 0x00;
        bufResponse[offset++] = 0x00;

        // ARCOUNT = 0
        bufResponse[offset++] = 0x00;
        bufResponse[offset++] = 0x00;

        // ===== QUESTION SECTION =====

        // QNAME: codecrafters.io
        bufResponse[offset++] = 0x0c; // "codecrafters"
        byte[] label1 = "codecrafters".getBytes();
        System.arraycopy(label1, 0, bufResponse, offset, label1.length);
        offset += label1.length;

        bufResponse[offset++] = 0x02; // "io"
        byte[] label2 = "io".getBytes();
        System.arraycopy(label2, 0, bufResponse, offset, label2.length);
        offset += label2.length;

        bufResponse[offset++] = 0x00; // terminator

        // QTYPE = A (1)
        bufResponse[offset++] = 0x00;
        bufResponse[offset++] = 0x01;

        // QCLASS = IN (1)
        bufResponse[offset++] = 0x00;
        bufResponse[offset++] = 0x01;

        // ===== ANSWER SECTION (NEW) =====

        // NAME: codecrafters.io
        bufResponse[offset++] = 0x0c;
        System.arraycopy(label1, 0, bufResponse, offset, label1.length);
        offset += label1.length;

        bufResponse[offset++] = 0x02;
        System.arraycopy(label2, 0, bufResponse, offset, label2.length);
        offset += label2.length;

        bufResponse[offset++] = 0x00;

        // TYPE = A (1)
        bufResponse[offset++] = 0x00;
        bufResponse[offset++] = 0x01;

        // CLASS = IN (1)
        bufResponse[offset++] = 0x00;
        bufResponse[offset++] = 0x01;

        // TTL = 60 seconds
        bufResponse[offset++] = 0x00;
        bufResponse[offset++] = 0x00;
        bufResponse[offset++] = 0x00;
        bufResponse[offset++] = 0x3c;

        // RDLENGTH = 4
        bufResponse[offset++] = 0x00;
        bufResponse[offset++] = 0x04;

        // RDATA = 8.8.8.8
        bufResponse[offset++] = 0x08;
        bufResponse[offset++] = 0x08;
        bufResponse[offset++] = 0x08;
        bufResponse[offset++] = 0x08;

        DatagramPacket responsePacket =
            new DatagramPacket(bufResponse, offset, packet.getSocketAddress());

        serverSocket.send(responsePacket);
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }
}
