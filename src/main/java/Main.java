import java.io.*;
import java.net.*;
import java.util.*;

public class Main {

  static class NameResult {
    byte[] name;
    int nextOffset;
  }

  static NameResult readName(byte[] packet, int offset) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int originalOffset = offset;
    boolean jumped = false;

    while (true) {
      int len = packet[offset] & 0xFF;

      if ((len & 0xC0) == 0xC0) {
        int pointer =
            ((len & 0x3F) << 8) | (packet[offset + 1] & 0xFF);
        offset = pointer;
        jumped = true;
        continue;
      }

      if (len == 0) {
        out.write(0);
        offset++;
        break;
      }

      out.write(len);
      offset++;
      out.write(packet, offset, len);
      offset += len;
    }

    NameResult res = new NameResult();
    res.name = out.toByteArray();
    res.nextOffset = jumped ? originalOffset + 2 : offset;
    return res;
  }

  public static void main(String[] args) throws Exception {
    DatagramSocket socket = new DatagramSocket(2053);

    while (true) {
      byte[] req = new byte[512];
      DatagramPacket packet = new DatagramPacket(req, req.length);
      socket.receive(packet);

      int id = ((req[0] & 0xFF) << 8) | (req[1] & 0xFF);
      int flags = ((req[2] & 0xFF) << 8) | (req[3] & 0xFF);
      int opcode = (flags >> 11) & 0xF;
      int rd = (flags >> 8) & 1;

      int qdcount = ((req[4] & 0xFF) << 8) | (req[5] & 0xFF);

      int offset = 12;
      List<byte[]> names = new ArrayList<>();

      for (int i = 0; i < qdcount; i++) {
        NameResult nr = readName(req, offset);
        names.add(nr.name);
        offset = nr.nextOffset + 4; // skip QTYPE + QCLASS
      }

      byte[] res = new byte[512];
      int ro = 0;

      // Header
      res[ro++] = (byte) (id >> 8);
      res[ro++] = (byte) id;

      int rf = (1 << 15) | (opcode << 11) | (rd << 8);
      if (opcode != 0) rf |= 4;

      res[ro++] = (byte) (rf >> 8);
      res[ro++] = (byte) rf;

      res[ro++] = 0x00;
      res[ro++] = (byte) qdcount;

      res[ro++] = 0x00;
      res[ro++] = (byte) qdcount;

      res[ro++] = 0x00;
      res[ro++] = 0x00;
      res[ro++] = 0x00;
      res[ro++] = 0x00;

      // Questions
      for (byte[] n : names) {
        System.arraycopy(n, 0, res, ro, n.length);
        ro += n.length;
        res[ro++] = 0x00;
        res[ro++] = 0x01;
        res[ro++] = 0x00;
        res[ro++] = 0x01;
      }

      // Answers
      for (byte[] n : names) {
        System.arraycopy(n, 0, res, ro, n.length);
        ro += n.length;
        res[ro++] = 0x00;
        res[ro++] = 0x01;
        res[ro++] = 0x00;
        res[ro++] = 0x01;

        res[ro++] = 0x00;
        res[ro++] = 0x00;
        res[ro++] = 0x00;
        res[ro++] = 0x3c;

        res[ro++] = 0x00;
        res[ro++] = 0x04;

        res[ro++] = 0x08;
        res[ro++] = 0x08;
        res[ro++] = 0x08;
        res[ro++] = 0x08;
      }

      socket.send(new DatagramPacket(res, ro, packet.getSocketAddress()));
    }
  }
}
