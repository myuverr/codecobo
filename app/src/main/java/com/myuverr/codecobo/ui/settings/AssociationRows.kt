// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.settings

import com.myuverr.codecobo.core.DeviceProfile
import com.myuverr.codecobo.ui.management.OwnAssociation

/** How an association is named when the system does not provide a usable name. */
internal sealed interface AssociationLabel {
    data class Named(val name: String) : AssociationLabel
    data class AddressSuffix(val suffix: String) : AssociationLabel
    data class Identifier(val id: Int) : AssociationLabel
}

/** A name worth showing; blank names fall back to the address or the association id. */
private val OwnAssociation.displayName: String? get() = name?.takeIf(String::isNotBlank)

internal data class AssociationRow(
    val association: OwnAssociation,
    val saved: Boolean,
    /** Non-blank names shared with another association need an address suffix to tell rows apart. */
    val duplicateName: Boolean,
) {
    val label: AssociationLabel
        get() = association.displayName?.let(AssociationLabel::Named)
            ?: association.address?.let { AssociationLabel.AddressSuffix(it.takeLast(5)) }
            ?: AssociationLabel.Identifier(association.id)

    val disambiguator: String?
        get() = if (duplicateName) association.address?.takeLast(5) ?: association.id.toString() else null
}

/** Saved lookups and duplicate-name counts are per-list facts, not per-row queries. */
internal fun associationRows(
    associations: List<OwnAssociation>,
    profiles: List<DeviceProfile>,
): List<AssociationRow> {
    val savedAddresses = profiles.mapNotNull { it.address()?.lowercase() }.toSet()
    val nameCounts = associations.mapNotNull { it.displayName }.groupingBy { it }.eachCount()
    return associations.map { association ->
        val name = association.displayName
        AssociationRow(
            association = association,
            saved = association.address?.lowercase() in savedAddresses,
            duplicateName = name != null && (nameCounts[name] ?: 0) > 1,
        )
    }
}
