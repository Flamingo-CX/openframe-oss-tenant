package cluster

import (
	"github.com/flamingo/openframe/internal/cluster/domain"
	"github.com/flamingo/openframe/internal/cluster/prerequisites"
	"github.com/flamingo/openframe/internal/cluster/utils"
	"github.com/flamingo/openframe/internal/shared/ui"
	"github.com/spf13/cobra"
)

// GetClusterCmd returns the cluster command and its subcommands
func GetClusterCmd() *cobra.Command {
	// Initialize global flags
	utils.InitGlobalFlags()
	
	clusterCmd := &cobra.Command{
		Use:     "cluster",
		Aliases: []string{"k"},
		Short:   "Manage Kubernetes clusters",
		Long: `Cluster Management - Create, manage, and clean up Kubernetes clusters

This command group provides cluster lifecycle management functionality:
  • create - Create a new cluster with interactive configuration
  • delete - Remove a cluster and clean up resources  
  • list - Show all managed clusters
  • status - Display detailed cluster information
  • cleanup - Remove unused images and resources

Supports K3d clusters for local development.

Examples:
  openframe cluster create
  openframe cluster delete`,
		PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
			// Check prerequisites before running any cluster command
			return prerequisites.CheckPrerequisites()
		},
		RunE: func(cmd *cobra.Command, args []string) error {
			// Show logo when no subcommand is provided
			ui.ShowLogo()
			return cmd.Help()
		},
	}

	// Add subcommands - much simpler now
	clusterCmd.AddCommand(
		getCreateCmd(),
		getDeleteCmd(),
		getListCmd(),
		getStatusCmd(),
		getCleanupCmd(),
	)

	// Add global flags
	domain.AddGlobalFlags(clusterCmd, utils.GetGlobalFlags().Global)

	return clusterCmd
}

