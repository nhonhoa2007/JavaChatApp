package org.example.common.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// Entity lưu lịch sử cuộc gọi
@Entity
@Table(name = "CallLogs")
public class CallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "caller_id", nullable = false)
    private User caller;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "callee_id", nullable = false)
    private User callee;

    // "VOICE" hoặc "VIDEO"
    @Column(name = "call_type", length = 10, nullable = false)
    private String callType;

    // COMPLETED, MISSED, REJECTED, CANCELED, FAILED
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    // null nếu cuộc gọi chưa được kết nối
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    // Thời lượng (giây), null nếu chưa kết nối
    @Column(name = "duration_sec")
    private Integer durationSec;

    public CallLog() {}

    public CallLog(User caller, User callee, String callType, String status, LocalDateTime startedAt) {
        this.caller = caller;
        this.callee = callee;
        this.callType = callType;
        this.status = status;
        this.startedAt = startedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getCaller() { return caller; }
    public void setCaller(User caller) { this.caller = caller; }

    public User getCallee() { return callee; }
    public void setCallee(User callee) { this.callee = callee; }

    public String getCallType() { return callType; }
    public void setCallType(String callType) { this.callType = callType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }

    public Integer getDurationSec() { return durationSec; }
    public void setDurationSec(Integer durationSec) { this.durationSec = durationSec; }
}
