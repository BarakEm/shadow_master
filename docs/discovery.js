/**
 * Network Discovery for Shadow Master Backend
 *
 * This module handles automatic discovery of Shadow Master backends
 * on the local network. It tries multiple approaches:
 * 1. localhost (for same-machine backends)
 * 2. common local IPs (192.168.x.x, 10.x.x.x)
 * 3. mDNS/Zeroconf (if browser supports it)
 */

const DISCOVERY_TIMEOUT = 2000; // 2 seconds per probe
const COMMON_PORTS = [8765]; // Shadow Master default port

class BackendDiscovery {
    constructor() {
        this.discoveredBackends = [];
        this.activeBackend = null;
    }

    /**
     * Discover Shadow Master backends on the network.
     * Returns the first working backend found.
     */
    async discover() {
        console.log('🔍 Starting backend discovery...');

        // Try localhost first (fastest)
        const localhostUrl = 'http://localhost:8765';
        if (await this.probeBackend(localhostUrl)) {
            console.log('✓ Found backend on localhost');
            this.activeBackend = localhostUrl;
            return localhostUrl;
        }

        // Try common local network IPs
        const localIPs = this.generateLocalIPs();
        for (const ip of localIPs) {
            for (const port of COMMON_PORTS) {
                const url = `http://${ip}:${port}`;
                if (await this.probeBackend(url)) {
                    console.log(`✓ Found backend at ${url}`);
                    this.activeBackend = url;
                    return url;
                }
            }
        }

        console.log('✗ No backend found');
        return null;
    }

    /**
     * Generate common local network IP ranges to scan.
     */
    generateLocalIPs() {
        const ips = [];

        // Common router IPs
        ips.push('192.168.1.1', '192.168.0.1', '192.168.1.100', '10.0.0.1');

        // Scan a small range of common LAN IPs
        // (Limited to avoid being too intrusive)
        for (let i = 1; i <= 10; i++) {
            ips.push(`192.168.1.${i}`);
            ips.push(`192.168.0.${i}`);
        }

        return ips;
    }

    /**
     * Probe a backend URL to see if it responds.
     */
    async probeBackend(url) {
        try {
            const controller = new AbortController();
            const timeout = setTimeout(() => controller.abort(), DISCOVERY_TIMEOUT);

            const response = await fetch(`${url}/api/health`, {
                signal: controller.signal,
                mode: 'cors'
            });

            clearTimeout(timeout);

            if (response.ok) {
                const data = await response.json();
                if (data.status === 'ok') {
                    // Get detailed info about this backend
                    try {
                        const infoResponse = await fetch(`${url}/api/discovery/info`);
                        const info = await infoResponse.json();
                        this.discoveredBackends.push({ url, info });
                    } catch (e) {
                        this.discoveredBackends.push({ url, info: null });
                    }
                    return true;
                }
            }
        } catch (error) {
            // Silently fail - this is expected for non-existent backends
        }
        return false;
    }

    /**
     * Get all discovered backends.
     */
    getDiscoveredBackends() {
        return this.discoveredBackends;
    }

    /**
     * Get the active backend URL.
     */
    getActiveBackend() {
        return this.activeBackend;
    }

    /**
     * Set the active backend URL manually.
     */
    setActiveBackend(url) {
        this.activeBackend = url;
    }
}

// Export for use in app.js
const backendDiscovery = new BackendDiscovery();
