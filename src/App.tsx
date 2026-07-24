/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { useEffect, useState, useRef } from 'react';
import { Search, Play, Pause, ArrowLeft, Loader2 } from 'lucide-react';
import { searchTracks, getStreamUrl } from './api';
import { TrackItem } from './types';

export default function App() {
  const [screen, setScreen] = useState<'splash' | 'main' | 'player'>('splash');
  const [searchQuery, setSearchQuery] = useState('');
  const [tracks, setTracks] = useState<TrackItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  
  const [currentTrack, setCurrentTrack] = useState<TrackItem | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  
  const audioRef = useRef<HTMLAudioElement | null>(null);

  // Splash Screen Timer
  useEffect(() => {
    if (screen === 'splash') {
      const timer = setTimeout(() => setScreen('main'), 2000);
      return () => clearTimeout(timer);
    }
  }, [screen]);

  // Audio Events Setup
  useEffect(() => {
    if (!audioRef.current) {
      audioRef.current = new Audio();
    }
    
    const audio = audioRef.current;
    
    const onTimeUpdate = () => setProgress(audio.currentTime);
    const onLoadedMetadata = () => setDuration(audio.duration);
    const onEnded = () => setIsPlaying(false);
    
    audio.addEventListener('timeupdate', onTimeUpdate);
    audio.addEventListener('loadedmetadata', onLoadedMetadata);
    audio.addEventListener('ended', onEnded);
    
    return () => {
      audio.removeEventListener('timeupdate', onTimeUpdate);
      audio.removeEventListener('loadedmetadata', onLoadedMetadata);
      audio.removeEventListener('ended', onEnded);
    };
  }, []);

  const handleSearch = async () => {
    if (!searchQuery.trim()) return;
    setLoading(true);
    setError('');
    try {
      const results = await searchTracks(searchQuery);
      setTracks(results);
    } catch (err: any) {
      setError(err.message || 'Failed to search tracks');
    } finally {
      setLoading(false);
    }
  };

  const handlePlayTrack = async (track: TrackItem) => {
    if (!track.transcodingsUrl) {
      setError('Stream unavailable for this track');
      return;
    }
    
    setCurrentTrack(track);
    setScreen('player');
    setLoading(true);
    setError('');
    
    try {
      const streamUrl = await getStreamUrl(track.transcodingsUrl);
      if (audioRef.current) {
        audioRef.current.src = streamUrl;
        await audioRef.current.play();
        setIsPlaying(true);
      }
    } catch (err: any) {
      setError('Failed to load stream');
    } finally {
      setLoading(false);
    }
  };

  const togglePlayPause = () => {
    if (audioRef.current) {
      if (isPlaying) {
        audioRef.current.pause();
      } else {
        audioRef.current.play();
      }
      setIsPlaying(!isPlaying);
    }
  };

  const handleSeek = (e: React.ChangeEvent<HTMLInputElement>) => {
    const time = Number(e.target.value);
    if (audioRef.current) {
      audioRef.current.currentTime = time;
      setProgress(time);
    }
  };

  const formatTime = (seconds: number) => {
    if (!seconds || isNaN(seconds)) return '00:00';
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  return (
    <div className="min-h-screen bg-gray-900 flex flex-col items-center justify-center p-4 sm:p-8 font-sans">
      <div className="mb-6 text-center">
        <h1 className="text-2xl font-bold text-white mb-2">BerryMusic Web Preview</h1>
        <p className="text-gray-400 text-sm max-w-md">Simulating the BlackBerry Q10 (720x720) Android app layout and live SoundCloud API workflow.</p>
      </div>

      {/* BlackBerry Q10 Simulator Wrapper */}
      <div className="relative bg-[#000] p-4 sm:p-6 rounded-[2rem] shadow-2xl border-4 border-gray-800">
        
        {/* Device Brand Header */}
        <div className="absolute top-2 left-0 right-0 flex justify-center">
          <span className="text-[10px] text-gray-500 font-bold tracking-widest">BLACKBERRY</span>
        </div>

        {/* Square Screen Area (720x720 aspect ratio simulated) */}
        <div className="relative w-[320px] h-[320px] sm:w-[500px] sm:h-[500px] bg-[#1a1a2e] overflow-hidden rounded-md border border-gray-900 mt-2">
          
          {/* --- SPLASH SCREEN --- */}
          {screen === 'splash' && (
            <div className="absolute inset-0 flex items-center justify-center bg-gradient-to-br from-[#1a1a2e] to-[#0a0a1a]">
              <div className="w-48 h-48 rounded-full bg-[#1a1a2e] shadow-[0_0_50px_rgba(0,0,0,0.5)] flex items-center justify-center border border-gray-800">
                <h1 className="text-2xl sm:text-3xl font-bold text-white tracking-wider">BerryMusic</h1>
              </div>
            </div>
          )}

          {/* --- MAIN SCREEN (SEARCH & LIST) --- */}
          {screen === 'main' && (
            <div className="absolute inset-0 flex flex-col bg-[#121212]">
              {/* Header */}
              <div className="p-4 sm:p-6 pb-2">
                 <h2 className="text-white text-2xl font-bold tracking-wide">BerryMusic</h2>
              </div>
              {/* Top Search Bar */}
              <div className="px-4 sm:px-6 pb-4 flex gap-2 items-center z-10">
                <input
                  type="text"
                  placeholder="Search tracks or artists..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                  className="flex-1 bg-[#1E1E1E] text-white px-4 py-3 text-sm outline-none placeholder-gray-500 rounded-sm"
                />
                <button 
                  onClick={handleSearch}
                  className="bg-[#00B4D8] text-white px-4 py-3 hover:bg-[#0096b4] transition-colors rounded-sm font-medium"
                >
                  GO
                </button>
              </div>

              {/* Error Toast */}
              {error && (
                <div className="mx-4 mt-2 bg-red-900/50 text-red-200 px-3 py-1 text-xs text-center border border-red-800 rounded">
                  {error}
                </div>
              )}

              {/* List View */}
              <div className="flex-1 overflow-y-auto pb-4">
                {loading && tracks.length === 0 ? (
                  <div className="flex justify-center items-center h-32">
                    <Loader2 className="animate-spin text-white w-8 h-8" />
                  </div>
                ) : (
                  tracks.map((track) => (
                    <div 
                      key={track.id} 
                      onClick={() => handlePlayTrack(track)}
                      className="flex items-center gap-4 px-4 sm:px-6 py-3 hover:bg-[#1E1E1E] cursor-pointer transition-colors active:bg-[#2C2C2C]"
                    >
                      <div className="w-14 h-14 bg-[#1E1E1E] shrink-0 rounded-lg overflow-hidden">
                        {track.artworkUrl ? (
                          <img src={track.artworkUrl.replace('large', 't50x50')} alt="" className="w-full h-full object-cover" />
                        ) : (
                          <div className="w-full h-full bg-[#2C2C2C]" />
                        )}
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="text-white text-sm sm:text-base font-medium truncate">{track.title}</div>
                        <div className="text-[#888888] text-xs sm:text-sm truncate mt-0.5">{track.artist}</div>
                      </div>
                    </div>
                  ))
                )}
                {!loading && tracks.length === 0 && searchQuery && !error && (
                  <div className="text-center text-gray-500 mt-8 text-sm">No tracks found</div>
                )}
              </div>
            </div>
          )}

          {/* --- PLAYER SCREEN --- */}
          {screen === 'player' && currentTrack && (
            <div className="absolute inset-0 flex flex-col bg-gradient-to-b from-[#1E1E1E] to-[#0A0A0A]">
              <div className="p-4 flex items-center">
                <button 
                  onClick={() => setScreen('main')}
                  className="text-white p-2 hover:bg-[#2C2C2C] transition-colors rounded-full"
                >
                  <ArrowLeft size={24} />
                </button>
                <div className="flex-1 text-center text-[#00B4D8] font-bold text-xs tracking-widest pr-10">NOW PLAYING</div>
              </div>

              <div className="flex-1 flex flex-col items-center justify-center p-6 pb-2">
                {/* Artwork */}
                <div className="w-48 h-48 sm:w-64 sm:h-64 rounded-[2rem] overflow-hidden bg-[#1E1E1E] shadow-2xl mb-8">
                  {currentTrack.artworkUrl ? (
                    <img 
                      src={currentTrack.artworkUrl.replace('large', 't500x500')} 
                      alt="" 
                      className="w-full h-full object-cover" 
                    />
                  ) : (
                    <div className="w-full h-full bg-[#1E1E1E] flex items-center justify-center">
                      <Search size={40} className="text-[#666666]" />
                    </div>
                  )}
                </div>

                {/* Info */}
                <div className="w-full text-center px-4 mb-8">
                  <h2 className="text-white text-xl sm:text-2xl font-bold truncate mb-2">{currentTrack.title}</h2>
                  <p className="text-[#888888] text-sm sm:text-base truncate">{currentTrack.artist}</p>
                </div>

                {/* Progress */}
                <div className="w-full flex items-center gap-4 mb-8 text-xs text-[#888888] px-4">
                  <span>{formatTime(progress)}</span>
                  <input 
                    type="range" 
                    min={0} 
                    max={duration || currentTrack.duration / 1000} 
                    value={progress}
                    onChange={handleSeek}
                    className="flex-1 h-1 bg-[#2C2C2C] rounded-full appearance-none cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-3 [&::-webkit-slider-thumb]:h-3 [&::-webkit-slider-thumb]:bg-[#00B4D8] [&::-webkit-slider-thumb]:rounded-full"
                  />
                  <span>{formatTime(duration || currentTrack.duration / 1000)}</span>
                </div>

                {/* Controls */}
                <button 
                  onClick={togglePlayPause}
                  disabled={loading}
                  className="w-16 h-16 sm:w-20 sm:h-20 rounded-full bg-[#00B4D8] flex items-center justify-center text-white hover:bg-[#0096b4] transition-colors active:scale-95 disabled:opacity-50 shadow-lg shadow-[#00B4D8]/20"
                >
                  {loading ? (
                    <Loader2 size={28} className="animate-spin text-white" />
                  ) : isPlaying ? (
                    <Pause size={28} className="fill-current text-white" />
                  ) : (
                    <Play size={28} className="fill-current text-white ml-1" />
                  )}
                </button>
              </div>
            </div>
          )}
          
        </div>
        
        {/* Physical Keyboard Hint Simulator */}
        <div className="mt-4 pt-4 border-t border-gray-800 flex justify-center pb-2">
           <div className="text-gray-600 text-[10px] text-center max-w-[200px]">
             (Physical QWERTY Keyboard Area)
           </div>
        </div>
      </div>
    </div>
  );
}

