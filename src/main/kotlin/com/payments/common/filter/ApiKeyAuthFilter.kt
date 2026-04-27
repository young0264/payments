package com.payments.common.filter

import com.payments.merchant.repository.MerchantRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class ApiKeyAuthFilter(
    private val merchantRepository: MerchantRepository,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/actuator")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val apiKey = request.getHeader("X-API-KEY")
        if (apiKey == null || merchantRepository.findByApiKey(apiKey) == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API Key")
            return
        }
        filterChain.doFilter(request, response)
    }
}
