package com.example.sande.domain

import com.example.sande.data.entities.Member

object PermissionPolicy {
    fun canReadProducts(member: Member?): Boolean =
        member?.status == "ACTIVE" && member?.role in listOf("OWNER", "MANAGER", "SALESPERSON")

    fun canCreateProduct(member: Member?): Boolean =
        member?.status == "ACTIVE" && member?.role in listOf("OWNER", "MANAGER")

    fun canEditProduct(member: Member?): Boolean =
        member?.status == "ACTIVE" && member?.role in listOf("OWNER", "MANAGER")

    fun canDeleteProduct(member: Member?): Boolean =
        member?.status == "ACTIVE" && member?.role == "OWNER"

    fun canAdjustStock(member: Member?): Boolean =
        member?.status == "ACTIVE" && member?.role in listOf("OWNER", "MANAGER")

    fun canVoidSale(member: Member?): Boolean =
        member?.status == "ACTIVE" && member?.role in listOf("OWNER", "MANAGER")

    fun canRecordSale(member: Member?): Boolean =
        member?.status == "ACTIVE" && member?.role in listOf("OWNER", "MANAGER", "SALESPERSON")

    fun canReadReports(member: Member?): Boolean =
        member?.status == "ACTIVE" && member?.role in listOf("OWNER", "MANAGER")

    fun canManageMembers(member: Member?): Boolean =
        member?.status == "ACTIVE" && member?.role == "OWNER"

    fun isOwner(member: Member?): Boolean = member?.role == "OWNER"
    fun isActive(member: Member?): Boolean = member?.status == "ACTIVE"
}
