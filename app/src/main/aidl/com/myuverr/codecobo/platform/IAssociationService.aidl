// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform;
interface IAssociationService {
    void destroy() = 16777114;
    String associate(int userId, String address) = 1;
}
