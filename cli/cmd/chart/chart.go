package chart

import (
	"github.com/flamingo/openframe/internal/chart/prerequisites"
	"github.com/flamingo/openframe/internal/shared/ui"
	"github.com/spf13/cobra"
)

// GetChartCmd returns the chart command and its subcommands
func GetChartCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:     "chart",
		Aliases: []string{"c"},
		Short:   "Manage Helm charts",
		Long: `Chart Management - Install and manage ArgoCD

This command group provides ArgoCD chart lifecycle management:
  • install - Install ArgoCD on a cluster

Requires an existing cluster created with 'openframe cluster create'.

Examples:
  openframe chart install
  openframe chart install my-cluster`,
		PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
			return prerequisites.NewInstaller().CheckAndInstall()
		},
		RunE: func(cmd *cobra.Command, args []string) error {
			ui.ShowLogo()
			return cmd.Help()
		},
	}

	cmd.AddCommand(getInstallCmd())
	return cmd
}
