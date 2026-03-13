package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.PlatformFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PlatformFeeRepository extends JpaRepository<PlatformFee, UUID> {
}
