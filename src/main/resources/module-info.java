module org.example {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;
    requires java.sql;
    requires jakarta.persistence;
    requires org.hibernate.orm.core;
    requires jbcrypt;
    
    // Mở các gói (packages) cho phép JavaFX Reflection truy cập
    opens org.example.client.controller to javafx.fxml, javafx.base;
    opens org.example.common.model to org.hibernate.orm.core;

    // Xuất khẩu class khởi chạy JavaFX
    exports org.example.client;
}
