import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Main {
  private static void setPacketId(byte[] response, int id) {
    response[0] = (byte)((id >> 8) & 0xFF);
    response[1] = (byte)(id & 0xFF);
  }
  public static void main(String[] args){
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    System.out.println("Logs from your program will appear here!");

    // TODO: Uncomment the code below to pass the first stage
    
    try(DatagramSocket serverSocket = new DatagramSocket(2053)) {
      while(true) {
        final byte[] buf = new byte[512];
        setPacketId(bufResponse, 1234);
        setQR(bufResponse);
        final DatagramPacket packet = new DatagramPacket(buf, buf.length);
        serverSocket.receive(packet);
        System.out.println("Received data");
    
        final byte[] bufResponse = new byte[512];
        final DatagramPacket packetResponse = new DatagramPacket(bufResponse, bufResponse.length, packet.getSocketAddress());
        serverSocket.send(packetResponse);
      }
    } catch (IOException e) {
        System.out.println("IOException: " + e.getMessage());
    }
  }
}
