'use client'

import styles from './TopStatusBar.module.css'
import { ConnectionMode } from '@/types'

interface TopStatusBarProps {
  mode: ConnectionMode
  nodeCount: number
  emergencyCount: number
  internetConnected: boolean
  onToggleInternet: () => void
}

export function TopStatusBar({
  mode,
  nodeCount,
  emergencyCount,
  internetConnected,
  onToggleInternet
}: TopStatusBarProps) {
  return (
    <header className={styles['top-status-bar']}>
      <div className={styles.logo}>
        <span className={styles['logo-icon']}>⬡</span>
        <span className={styles['logo-text']}>ResQMesh</span>
        <span className={`${styles['mode-badge']} ${mode === 'CLOUD' ? styles['mode-cloud'] : styles['mode-mesh']}`}>
          {mode} MODE
        </span>
      </div>

      <div className={styles['status-indicators']}>
        <div className={styles['status-item']}>
          <span className={`${styles['status-value']} ${styles['status-green']}`}>{nodeCount}</span>
          <span className={styles['status-label']}>Nodes Active</span>
        </div>

        <div className={styles['status-item']}>
          <span className={`${styles['status-value']} ${emergencyCount > 0 ? styles['status-red'] : styles['status-muted']}`}>
            {emergencyCount}
          </span>
          <span className={styles['status-label']}>Emergencies</span>
        </div>

        <div className={styles['status-item']}>
          <span className={`${styles['status-value']} ${internetConnected ? styles['status-green'] : styles['status-red']}`}>
            {internetConnected ? 'Connected' : 'Lost'}
          </span>
          <span className={styles['status-label']}>Internet</span>
        </div>

        <div className={styles['status-item']}>
          <span className={`${styles['status-value']} ${mode === 'MESH' ? styles['status-orange'] : styles['status-blue']}`}>
            {mode === 'MESH' ? 'Active' : 'Active'}
          </span>
          <span className={styles['status-label']}>Mesh Status</span>
        </div>

        <button
          className={styles['demo-toggle-btn']}
          onClick={onToggleInternet}
        >
          {internetConnected ? 'Simulate Failure' : 'Restore Internet'}
        </button>
      </div>
    </header>
  )
}
