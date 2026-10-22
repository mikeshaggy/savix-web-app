/** @type {import('next').NextConfig} */
const nextConfig = {
    output: 'standalone',
    // Stage 6.4: the Analytics Overview was removed; old links land on the forecast
    async redirects() {
        return [
            { source: '/analytics/overview', destination: '/analytics/forecast', permanent: false },
        ];
    },
};

export default nextConfig;
