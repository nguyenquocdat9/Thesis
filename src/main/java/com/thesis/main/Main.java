package com.thesis.main;

import java.io.FileWriter;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        String fileName = "dataset-energy/energy-workload.csv";
        try (FileWriter writer = new FileWriter(fileName)) {
            double time = 0.0;
            // Sinh 1000 request để tạo độ trễ và tải cho Host
            for (int i = 0; i < 1000; i++) {
                time += 0.01; // Giảm khoảng cách xuống 0.01s để tạo sự dồn dập (Burst Traffic)

                // Tăng CloudletLen lên 50,000 (Gấp 10 lần cũ)
                writer.write(String.format("%.2f,web_vnf,100,50000\n", time));

                if (i % 2 == 0) { // Tăng tần suất của các VNF khác
                    writer.write(String.format("%.2f,firewall_vnf,100,10000\n", time));
                    writer.write(String.format("%.2f,db_vnf,100,10000\n", time));
                }
            }
            System.out.println("✅ Đã sinh xong 1000 dòng traffic tại: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}