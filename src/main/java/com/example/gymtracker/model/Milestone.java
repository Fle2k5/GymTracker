package com.example.gymtracker.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;

@Entity
@Table(name = "milestones")
public class Milestone {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;
    @Column(nullable = false, length = 140) private String title;
    @Column(name = "milestone_date", nullable = false) private LocalDate date;
    @Column(length = 600) private String description;
    @Column(name = "image_name", nullable = false, length = 180) private String imageName;
    @Column(name = "image_type", nullable = false, length = 80) private String imageType;
    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.LONG32VARBINARY)
    @Column(name = "image_data", nullable = false)
    private byte[] imageData;

    protected Milestone() {}

    public Milestone(AppUser owner, String title, LocalDate date, String description, String imageName, String imageType, byte[] imageData) {
        this.owner = owner;
        this.title = title; this.date = date; this.description = description;
        this.imageName = imageName; this.imageType = imageType; this.imageData = imageData;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getTitle() { return title; }
    public LocalDate getDate() { return date; }
    public String getDescription() { return description; }
    public String getImageName() { return imageName; }
    public String getImageType() { return imageType; }
    public byte[] getImageData() { return imageData; }
}
