package com.example.sande.domain

import com.example.sande.data.entities.Member
import com.example.sande.data.repository.AuthRepository

object PermissionPolicy {
    fun canReadProducts(session: AuthRepository.Session?): Boolean =
        session != null

    fun canCreateProduct(session: AuthRepository.Session?): Boolean =
        session?.role == "OWNER"

    fun canEditProduct(session: AuthRepository.Session?): Boolean =
        session?.role == "OWNER"

    fun canDeleteProduct(session: AuthRepository.Session?): Boolean =
        session?.role == "OWNER"

    fun canAdjustStock(session: AuthRepository.Session?): Boolean =
        session?.role == "OWNER"

    fun canReceiveStock(session: AuthRepository.Session?): Boolean =
        session?.role == "OWNER"

    fun canVoidSale(session: AuthRepository.Session?): Boolean =
        session?.role == "OWNER"

    fun canRecordSale(session: AuthRepository.Session?): Boolean =
        session != null

    fun canReadAllSales(session: AuthRepository.Session?): Boolean =
        session?.role == "OWNER"

    fun canReadOwnSales(session: AuthRepository.Session?): Boolean =
        session != null

    fun canManageMembers(session: AuthRepository.Session?): Boolean =
        session?.role == "OWNER"

    fun isOwner(session: AuthRepository.Session?): Boolean = session?.role == "OWNER"
    fun isSalesperson(session: AuthRepository.Session?): Boolean = session?.role == "SALESPERSON"
    fun isSignedIn(session: AuthRepository.Session?): Boolean = session != null
}
