import { TrackItem } from './types';

const CLIENT_IDS = [
  "Mxv2e5wxnWei6krLywjIXpztX7S0VCeK", // Extracted web client_id (Working)
  "a3e059563d7fd3372b49b37f00a00bda", // Legacy Android 3.x
  "bgzZzE15wK8v0N0X9U1rXl6g7PZ2eZ7s",
  "02gUJC0hH2ct1EGOcYXQIzRFU91c72Ea"
];
let currentClientIndex = 0;
const API_BASE = "https://api-v2.soundcloud.com";

export async function searchTracks(query: string, retryCount = 0): Promise<TrackItem[]> {
  const targetUrl = `${API_BASE}/search/tracks?q=${encodeURIComponent(query)}&limit=20&client_id=${CLIENT_IDS[currentClientIndex]}`;
  const res = await fetch(`/api/proxy?url=${encodeURIComponent(targetUrl)}`);
  
  if (!res.ok) {
    if ((res.status === 401 || res.status === 403) && retryCount < CLIENT_IDS.length) {
        currentClientIndex = (currentClientIndex + 1) % CLIENT_IDS.length;
        return searchTracks(query, retryCount + 1);
    }
    throw new Error(`API Error: ${res.status}`);
  }
  
  const data = await res.json();
  
  return data.collection.map((track: any) => {
    let transcodingsUrl = null;
    if (track.media?.transcodings) {
      const progressive = track.media.transcodings.find((t: any) => t.format?.protocol === 'progressive');
      if (progressive) transcodingsUrl = progressive.url;
    }
    
    return {
      id: String(track.id),
      title: track.title,
      artist: track.user?.username || 'Unknown',
      artworkUrl: track.artwork_url || track.user?.avatar_url,
      duration: track.duration,
      transcodingsUrl
    };
  });
}

export async function getStreamUrl(transcodingsUrl: string, retryCount = 0): Promise<string> {
  const targetUrl = `${transcodingsUrl}?client_id=${CLIENT_IDS[currentClientIndex]}`;
  const res = await fetch(`/api/proxy?url=${encodeURIComponent(targetUrl)}`);
  
  if (!res.ok) {
    if ((res.status === 401 || res.status === 403) && retryCount < CLIENT_IDS.length) {
      currentClientIndex = (currentClientIndex + 1) % CLIENT_IDS.length;
      return getStreamUrl(transcodingsUrl, retryCount + 1);
    }
    throw new Error(`API Error: ${res.status}`);
  }
  
  const data = await res.json();
  return data.url;
}
