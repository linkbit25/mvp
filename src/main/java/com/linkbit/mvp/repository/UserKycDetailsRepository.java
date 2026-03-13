package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.UserKycDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserKycDetailsRepository extends JpaRepository<UserKycDetails, UUID> {
}
